import java.io.*;
import java.nio.file.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class represents the server in the auction system.
 * It implements the Auction interface for remote method invocation.
 * Uses asymmetric cryptographic authentication.
 */
public class Server implements Auction {
    private List<AuctionItem> auctionItems;
    private Map<Integer, String> registeredUsers;
    private Map<Integer, PublicKey> userPubKeys;
    private Map<Integer, String> userTokens;
    private Map<Integer, String> userChallenges;
    private List<Replica> replicas;
    private int primaryReplicaID;

    /**
     * Constructor for the Server class.
     * Initialises the server with necessary data structures and generates/reads the server's public key.
     */
    public Server() throws RemoteException {
        auctionItems = new ArrayList<>();
        registeredUsers = new HashMap<>();
        userPubKeys = new HashMap<>();
        userTokens = new ConcurrentHashMap<>();
        userChallenges = new ConcurrentHashMap<>();
        replicas = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            Replica replica = new Replica(i);
            replicas.add(replica);
        }
        primaryReplicaID = 3;

        try {
            File kFile = new File("../keys/serverKey.pub");
            PublicKey serverPubKey = kFile.exists() ? readPublicKey(kFile) : generateAndStoreServerKey();
        } catch (Exception e) {
            throw new RemoteException("Error initializing server.", e);
        }
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
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        return primaryReplicaID;
    }

    /**
     * Stores the provided public key to a file.
     *
     * @param publicKey the public key to be stored
     * @param filePath  the path to the file
     */
    public void storePublicKey(PublicKey publicKey, String filePath) throws IOException {
        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(publicKeyBase64.getBytes());
        }
    }

    /**
     * Reads a public key from the specified file.
     *
     * @param file the file containing the public key
     * @return the PublicKey object read from the file
     * @throws IOException            if an I/O-related exception occurs
     */
    private PublicKey readPublicKey(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream f = new ObjectInputStream(new FileInputStream(file))) {
            return (PublicKey) f.readObject();
        }
    }

    /**
     * Generates a new key pair, stores the public key, and returns the public key.
     *
     * @return the generated public key
     * @throws Exception if an error occurs during key pair generation or public key storage
     */
    private PublicKey generateAndStoreServerKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        PublicKey serverPubKey = pair.getPublic();
        storePublicKey(serverPubKey, "../keys/serverKey.pub");
        return serverPubKey;
    }

    /**
     * Loads a private key from the specified file.
     *
     * @param filePath the path to the file containing the private key
     * @return the PrivateKey object loaded from the file
     */
    private PrivateKey loadPrivateKey(String filePath) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(filePath));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /**
     * Main method to start the server and bind it to the RMI registry.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            Server s = new Server();
            String name = "myserver";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("Server ready");
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}
