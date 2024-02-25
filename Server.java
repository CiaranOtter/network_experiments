import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.EnumSet;


class ServerDataEvent {
    public NIOServer server;
    public SocketChannel socket;
    public byte[] data;

    public ServerDataEvent(NIOServer server, SocketChannel socket, byte[] data) {
        this.server = server;
        this.socket = socket;
        this.data = data ;
    }
}
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
class NIOServer implements Runnable  {
    private InetAddress hostname;
    private int port;
    private EchoWorker worker;

    private ServerSocketChannel serverChannel;

    private Selector selector;
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    private List changeRequests = new LinkedList();
    private Map pendingData = new HashMap();

    private Selector initSelector() throws IOException {
        Selector socketSelector = SelectorProvider.provider().openSelector();


        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        InetSocketAddress isa = new InetSocketAddress(this.hostname, this.port);
        serverChannel.socket().bind(isa);

        serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
        
        return socketSelector;
    }

    public NIOServer(InetAddress address, int port, EchoWorker worker) throws IOException{
        this.hostname = address;
        this.port = port;
        this.selector = this.initSelector();
        this.worker = worker;
    }


    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        Socket socket = socketChannel.socket();
        socketChannel.configureBlocking(false);

        socketChannel.register(this.selector, SelectionKey.OP_READ);

        System.out.println("new connection accepted");
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        System.out.println("reading data");

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

        this.worker.processData(this,socketChannel, this.readBuffer.array(), numRead);
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socketChannel);
            while(!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                if (buf.remaining() > 0) {
                    break;
                }
                queue.remove(0);
            }
            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            synchronized(this.pendingData) {
                List queue = (List) this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        this.selector.wakeup();
    }

    public void run() {
        while (true) {
            try {

                synchronized (this.changeRequests) {
                    Iterator changes = this.changeRequests.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
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
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e ) {
                e.printStackTrace();
            }
        }
    }
}

class EchoWorker implements Runnable {
    private List queue = new LinkedList();

    public void processData(NIOServer server, SocketChannel socket, byte[] data, int count) {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);

        System.out.println("Processing data");
        System.out.println(data);

        synchronized(queue) {
            queue.add(new ServerDataEvent(server, socket, dataCopy));
            queue.notify();
        }
    }

    public void run() {
        ServerDataEvent dataEvent;

        while (true) {
            synchronized (queue) {
                while(queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } 
                }
                dataEvent = (ServerDataEvent) queue.remove(0);
            }
            
            dataEvent.server.send(dataEvent.socket, dataEvent.data);
        }
    }
}

public class Server {
    public static void main(String[] args) {
        try {
            EchoWorker worker = new EchoWorker();

            new Thread(new NIOServer(null, 5000, worker)).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}