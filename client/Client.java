import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class Client{
     public static void main(String[] args) {
       if (args.length < 1) {
       System.out.println("Usage: java Client n");
       return;
       }
         int n = Integer.parseInt(args[0]); // n = itemId
         try {
               String name = "myserver";
               Registry registry = LocateRegistry.getRegistry("localhost");
               Auction server = (Auction) registry.lookup(name);
  
                //int result = server.factorial(n);
                //System.out.println("result is " + result);

                AuctionItem item = server.getSpec(n);

              if (item != null) {
                System.out.println("Item ID: " + item.getItemID());
                System.out.println("Name: " + item.getName());
                System.out.println("Description: " + item.getDescription());
                System.out.println("Highest Bid: " + item.getHighestBid());
              } else {
                System.out.println("Item not found.");
              }
              }
              catch (Exception e) {
               System.err.println("Exception:");
               e.printStackTrace();
               }
      }
}