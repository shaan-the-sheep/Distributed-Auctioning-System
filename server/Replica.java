import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class Replica implements Auction {
    private List<AuctionItem> auctionItems = new ArrayList<>();
    private Map<Integer, String> registeredUsers = new HashMap<>();
    private Map<Integer, PublicKey> userPubKeys = new HashMap<>();
    private int replicaID;

    public Replica(int replicaID) throws RemoteException {
        this.replicaID = replicaID;
        this.auctionItems = new ArrayList<>();
        this.registeredUsers = new HashMap<>();
        this.userPubKeys = new HashMap<>();

        startReplica();
    }

    public void startReplica() {
        try {
            Registry registry = LocateRegistry.getRegistry();
            String replicaName = "Replica" + replicaID;

            List<String> existingReplicas = Arrays.asList(registry.list());
            boolean replicaExists = existingReplicas.contains(replicaName);

            if (replicaExists) {
                Replica existingReplica = (Replica) registry.lookup(replicaName);
                copyDataFromReplica(existingReplica);
            } else {
                Replica replicaStub = (Replica) UnicastRemoteObject.exportObject(this, 0);
                registry.rebind(replicaName, replicaStub);
                System.out.println(replicaName + " ready");

                updateReplicas();
            }
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }

    private void updateReplicas() {
        try {
            Registry registry = LocateRegistry.getRegistry();
            List<Replica> existingReplicas = Arrays.stream(registry.list())
                    .filter(name -> name.startsWith("Replica") && !name.equals("Replica" + replicaID))
                    .map(name -> {
                        try {
                            return (Replica) registry.lookup(name);
                        } catch (Exception e) {
                            System.out.println("Failed to connect to: " + name);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            existingReplicas.forEach(backUp -> backUp.updateSharedVariable(auctionItems, registeredUsers, userPubKeys));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSharedVariable(List<AuctionItem> items, Map<Integer, String> reg, Map<Integer, PublicKey> keys) {
        auctionItems = new ArrayList<>(items);
        registeredUsers = new HashMap<>(reg);
        userPubKeys = new HashMap<>(keys);
    }

    private void copyDataFromReplica(Replica replica) {
        auctionItems = new ArrayList<>(replica.auctionItems);
        registeredUsers = new HashMap<>(replica.registeredUsers);
        userPubKeys = new HashMap<>(replica.userPubKeys);
    }


    /**
     * Registers a new user with the provided email and public key.
     *
     * @param email  the user's email
     * @param pubkey the user's public key
     * @return the assigned user ID
     */
    @Override
    public Integer register(String email, PublicKey pubkey) throws RemoteException {
        int userID = registeredUsers.size() + 1;
        registeredUsers.put(userID, email);
        userPubKeys.put(userID, pubkey);
        updateReplicas();
        return userID;
    }

    /**
     * Initiates a challenge for the specified user.
     *
     * @param userID          the user ID
     * @param clientChallenge the client's challenge
     * @return ChallengeInfo object containing the server's response signature and a random challenge for the client
     */
    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        return null;
    }

    /**
     * Authenticates the user based on the provided signature.
     *
     * @param userID    the user ID
     * @param signature the client's signature
     * @return TokenInfo object containing a one-time use token and its expiration time
     */
    @Override
    public TokenInfo authenticate(int userID, byte[] signature) throws RemoteException {
        return null;
    }

    /**
     * Retrieves the details of a specific auction item.
     *
     * @param userID the user ID
     * @param itemID the item ID
     * @param token  the user's token
     * @return the AuctionItem object representing the specified item
     */
    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
        return auctionItems.stream()
                .filter(item -> item.itemID == itemID)
                .findFirst()
                .orElse(null);
    }
    

    /**
     * Starts a new auction for the specified user.
     *
     * @param userID the user ID
     * @param item   the AuctionSaleItem object representing the item to be auctioned
     * @param token  the user's token
     * @return the ID assigned to the new auction item
     */
    @Override
    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException {
        int itemID = auctionItems.size() + 1;
        AuctionItem auctionItem = new AuctionItem();
        auctionItem.itemID = itemID;
        auctionItem.name = item.name;
        auctionItem.description = item.description;
        auctionItems.add(auctionItem);
        updateReplicas();
        return itemID;
    }

    /**
     * Retrieves an array of open auction items.
     *
     * @param userID the user ID
     * @param token  the user's token
     * @return an array of AuctionItem objects representing open auction items
     */
    @Override
    public AuctionItem[] listItems(int userID, String token) throws RemoteException {
        return auctionItems.toArray(new AuctionItem[0]);
    }

    /**
     * Closes an auction and returns the result.
     *
     * @param userID the user ID
     * @param itemID the item ID
     * @param token  the user's token
     * @return the AuctionResult object containing the winning email and price
     */
    @Override
    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException {
        AuctionItem item = getSpec(userID, itemID, token);

        AuctionResult result = new AuctionResult();
        result.winningEmail = registeredUsers.get(userID);
        result.winningPrice = item.highestBid;

        auctionItems.remove(item);
        updateReplicas();
        return result;
    }

    /**
     * Places a bid on a specific auction item.
     *
     * @param userID the user ID
     * @param itemID the item ID
     * @param price  the bid price
     * @param token  the user's token
     * @return true if the bid is successful, false otherwise
     */
    @Override
    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException {
        if (!registeredUsers.containsKey(userID)) {
            throw new RemoteException("User not registered.");
        }

        AuctionItem item = getSpec(userID, itemID, token);
        if (item == null) {
            throw new RemoteException("Item not found.");
        }

        if (price > item.highestBid) {
            item.highestBid = price;
            updateReplicas();
            return true;
        } else {
            updateReplicas();
            return false;
        }
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException{
        return replicaID;
    }

    // Getter method for auctionItems
    public List<AuctionItem> getAuctionItems() {
        return auctionItems;
    }

    // Getter method for registeredUsers
    public Map<Integer, String> getRegisteredUsers() {
        return registeredUsers;
    }

    // Getter method for userPubKeys
    public Map<Integer, PublicKey> getUserPubKeys() {
        return userPubKeys;
    }

    public static void main(String[] args) throws RemoteException {
        if (args.length < 1) {
            System.out.println("Usage: java Replica <replicaID>");
            return;
        }

        int replicaID = Integer.parseInt(args[0]);
        Replica replica = new Replica(replicaID);
    }
}