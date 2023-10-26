import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Auction extends Remote{ 
   AuctionItem getSpec(int itemID) throws RemoteException;
   //int factorial(int n) throws RemoteException;
}
