import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.List;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;


public class Server implements Auction {
    private List<AuctionItem> auctionItems;
    private SecretKey key;
    
    public Server() {
        auctionItems = new ArrayList<>();
        auctionItems.add(new AuctionItem(1, "Item 1", "Description 1", 100));
        auctionItems.add(new AuctionItem(2, "Item 2", "Description 2", 200));
        auctionItems.add(new AuctionItem(3, "Item 3", "Description 3", 300));

        try {
            File kFile = new File("../keys/testKey.aes");
            if (kFile.exists()) {
                // read from existing file
                ObjectInputStream f = new ObjectInputStream(new FileInputStream(kFile));
                key = (SecretKey) f.readObject();
                f.close();
            } else {
                //create file + generate keys
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                key = keyGenerator.generateKey();
                ObjectOutputStream f = new ObjectOutputStream(new FileOutputStream("../keys/testKey.aes"));
                f.writeObject(key);  
                f.close();       
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public SealedObject getSpec(int itemID) throws RemoteException {
        for (AuctionItem item : auctionItems) {
            if (item.getItemID() == itemID) {
                try {
                    // Encrypts item
                    Cipher cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.ENCRYPT_MODE, key);
                    return new SealedObject(item, cipher);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // item not found
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
