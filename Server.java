import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.ServerSocketChannel;
import java.net.ServerSocket;
import java.nio.channels.Selector;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.EnumSet;
import java.io.InputStream;
import java.nio.ByteBuffer;

class NIOServer implements Runnable {
    ServerSocketChannel serverChannel;
    Selector selector;
    ByteBuffer buffer;

    public NIOServer() throws IOException{
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        ServerSocket ss = serverChannel.socket();
        InetSocketAddress address = new InetSocketAddress(5000);
        ss.bind(address);
        serverChannel.configureBlocking(false);
        
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        buffer = ByteBuffer.allocate(1024);

    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        // selector.wakeup();
        client.register(selector,
        //  SelectionKey.OP_WRITE | 
        SelectionKey.OP_READ);
        System.out.println("New conection accepted" + client.getRemoteAddress());
        
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        buffer.clear();
        int size  = client.read(buffer);
        System.out.println((new String(buffer.array())).trim());
        
        if (size == -1) {
            System.out.println("Closing client");
            client.close();
            key.cancel();
            System.out.println("Connection closed by client");
            return;
        }

        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String message = new String(bytes).trim();
        System.out.println(message);
        // client.write(buffer);
    }

    // private void write(SelectionKey key) throws IOException {
    //     SocketChannel client = (SocketChannel) key.channel();
    //     // ByteBuffer buffer = ByteBuffer.allocate(256);
    //     buffer.flip();
    //     while (buffer.hasRemaining()) {
    //         if (client.write(buffer) == 0) {
    //             break;
    //         }
    //         // client.write(buffer);
    //     }
    //     buffer.clear();
    //     client.close();  
    // }

    @Override
    public void run() {
        while (true) {
            System.out.println("I am running in a loop");
            try {
                selector.select();
            }  catch (IOException e) {
                e.printStackTrace();
            }

            Set selectedKeys = selector.selectedKeys();
            Iterator iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();

                try {
                    if (key.isAcceptable()) {
                        System.out.println("running accept");
                        this.accept(key);
                    }

                    if (key.isReadable()) {
                        System.out.println("running read");;
                        this.read(key);
                    }

                    // if ((key.readyOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    //     System.out.println("running write");
                    //      this.write(key);
                    // }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}

public class Server {
    public static void main(String[] args) {
        try {
            new Thread(new NIOServer()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}