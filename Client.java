import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.Selector;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.EnumSet;
import java.nio.channels.AlreadyConnectedException;

class NIOClient implements Runnable {
    Selector selector;
    SocketChannel client;
    InetSocketAddress address;
    ByteBuffer buffer;

    public NIOClient() throws IOException {
        selector = Selector.open();
        client = SocketChannel.open();
        this.address = new InetSocketAddress("127.0.0.1", 5000);
        client.connect(address);
        client.configureBlocking(false);
        client.register(this.selector, SelectionKey.OP_CONNECT);

        buffer = ByteBuffer.allocate(1024);
        // client.configureBlocking(false); 
        // client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        // byte[] message = new String("this is a test to send to the server").getBytes();
        // ByteBuffer buffer = ByteBuffer.wrap(message);
        // socketClient.write(buffer);
        // buffer.clear();
        // ByteBuffer ackBuffer = ByteBuffer.allocate(256);
        // socketClient.read(ackBuffer);
        // String ackRes = new String(ackBuffer.array()).trim();

        // System.out.println("Acknowledgment from server: " + ackRes);
        // socketClient.close();
    }

    public void processData(String data) {
        System.out.println(data);
    }


    public void sendMessage(String message) throws IOException {
        client.write(ByteBuffer.wrap(message.getBytes()));
    }

    public void handleRead(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        buffer.clear();
        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            client.close();
            key.cancel();
            System.out.println("Connection closed by client: " + client.getRemoteAddress());
            return;
        }

        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String message = new String(bytes).trim();
        System.out.println("Received message from " + client.getRemoteAddress() + ": " + message);

    }

    public void handleConnection(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        while (!client.finishConnect()) {

        }
        client.register(this.selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        try {
            this.selector.select();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isConnectable()) {
                    this.handleConnection(key);
                    continue;
                }

                if (key.isReadable()) {
                    this.handleRead(key);
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}

public class Client {
    public static void main(String[] args) throws IOException {

            NIOClient client = new NIOClient();
            (new Thread(client)).start();
            start(client);
    }

    public static void start(NIOClient client) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Enter message: ");
                String message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    break;
                }
                client.sendMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
