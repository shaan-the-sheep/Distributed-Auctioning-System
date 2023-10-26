import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class Server implements Auction{
    private List<AuctionItem> auctionItems;
    
    public Server() {
        auctionItems = new ArrayList<>();
        auctionItems.add(new AuctionItem(1, "Item 1", "Description 1", 100));
        auctionItems.add(new AuctionItem(2, "Item 2", "Description 2", 200));
        auctionItems.add(new AuctionItem(3, "Item 3", "Description 3", 300));
        //super();
    }

    @Override
    public AuctionItem getSpec(int itemID) throws RemoteException {
        // Search for the auction item by itemID
        for (AuctionItem item : auctionItems) {
            if (item.getItemID() == itemID) {
                return item;
            }
        }
        // Return null if the item is not found
        return null;
    }


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
