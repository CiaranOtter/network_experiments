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

class ChangeRequest {
    public static final int REGISTER = 1;
    public static final int CHANGEOPS = 2;

    public SocketChannel socket;
    public int type;
    public int ops;

    public ChangeRequest(SocketChannel socket, int type, int ops) {
        this.socket = socket;
        this.type = type;
        this.ops = ops;
    }
}

class NIOClient implements Runnable  {
    private InetAddress hostname;
    private int port;
    private Map rspHandler = Collections.synchronizedMap(new HashMap());

    // private ServerSocketChannel ServerChannel;

    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    private List changeRequests = new LinkedList();
    private Map pendingData = new HashMap();

    private Selector initSelector() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

    private SocketChannel initiateConnection() throws IOException {
        System.out.println("Starting connection");
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        socketChannel.connect(new InetSocketAddress(this.hostname, this.port));

        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    public NIOClient(InetAddress address, int port) throws IOException{
        this.hostname = address;
        this.port = port;
        this.selector = this.initSelector();
    }


    private void finishConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel =  (SocketChannel) key.channel();

        try {
            socketChannel.finishConnect();
        } catch (IOException e) {
            key.cancel();
            return;
        }
        System.out.println("Connection complete");
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
        }
    }

    private void handleResponse(SocketChannel socketChannel, byte[] data, int numRead) throws IOException {
        byte[] rspData = new byte[numRead];
        System.arraycopy(data, 0, rspData, 0, numRead);

        RspHandler handler = (RspHandler) this.rspHandler.get(socketChannel);

        if (handler.handleResponse(rspData)) {
            socketChannel.close();
            socketChannel.keyFor(this.selector).cancel();
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        this.readBuffer.clear();

        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            key.channel().close();
            key.cancel();
            return;
        }

        this.handleResponse(socketChannel, this.readBuffer.array(), numRead);
    }

    private void write(SelectionKey key) throws IOException {
        System.out.println("writing data");
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socketChannel);
            while(!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                queue.remove(0);
                if (buf.remaining() > 0) {
                    break;
                }
                
            }
            System.out.println("write buffer has been emptied" + String.valueOf(queue.isEmpty()));

            if (queue.isEmpty()) {
                System.out.println("setting the type of channel to read");
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    public void send(byte[] data, RspHandler handler) throws IOException {
        SocketChannel socket = this.initiateConnection();

        this.rspHandler.put(socket, handler);
        
        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socket);
            if (queue == null) {
                System.out.println("peding data is null ");
                queue = new ArrayList();
                this.pendingData.put(socket, queue);
            }
            queue.add(ByteBuffer.wrap(data));
        }

        this.selector.wakeup();
    }

    @Override
    public void run() {
        while (true) {
            System.out.println("Running loop");
            try {

                synchronized (this.changeRequests) {
                    Iterator changes = this.changeRequests.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                                break;
                            case ChangeRequest.REGISTER:
                                change.socket.register(this.selector, change.ops);
                                break;
                        }
                    }
                    this.changeRequests.clear();
                }
                this.selector.select();

                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();

                    selectedKeys.remove();
            
                    if (!key.isValid()) {
                        System.out.println("Key is invalid");
                        continue;
                    }
                    
                    if (key.isConnectable()) {
                        System.out.println("Key is connectable");
                        this.finishConnection(key);
                    } else if (key.isReadable()) {
                        System.out.println("key is readable");
                        this.read(key);
                    } else if (key.isWritable()) {
                        System.out.println("key is writeable");
                        this.write(key);
                    }
                }
            } catch (Exception e ) {
                e.printStackTrace();
            }
        }
    }
}

class RspHandler {
    private byte[] rsp = null;

    public synchronized boolean handleResponse(byte[] rsp) {
        this.rsp = rsp;
        this.notify();
        return true;
    }

    public synchronized void waitForResponse() {
        while (this.rsp == null) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(this.rsp);
    }
}

public class Client {
    public static void main(String[] args) {
        try {
            NIOClient client = new NIOClient(InetAddress.getByName("127.0.0.1"), 5000);
            Thread t = new Thread(client);
            t.start();
            RspHandler handler = new RspHandler();
            client.send("Hello World\0".getBytes(), handler);
            handler.waitForResponse();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
