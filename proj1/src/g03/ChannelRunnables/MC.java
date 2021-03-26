package g03.ChannelRunnables;

import g03.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MC implements Runnable {

    private final Peer peer;

    public MC(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message message = new Message(peer.getMC().receive());
                if (message.getType() == MessageType.STORED) {
                    String key = message.getFileId() + "-" + message.getChunkNumber();

                    if (peer.getChunks().containsKey(key)) {
                        Chunk c = peer.getChunks().get(key);
                        c.getPeers().add(message.getSenderId());
                    } else {
                        Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree());
                        peer.getChunks().put(key, c);
                    }

                } else if (message.getType() == MessageType.GETCHUNK) {
                    String key = message.getFileId() + "-" + message.getChunkNumber();
                    if (peer.getChunks().containsKey(key)) {
                        String[] msgArgs = {peer.getProtocolVersion(),
                                String.valueOf(peer.getId()),
                                message.getFileId(),
                                String.valueOf(message.getChunkNumber())};

                        //TODO: refactor
                        byte[] body;
                        try (FileInputStream file = new FileInputStream(key)) {
                            body = file.readAllBytes();
                        }

                        //Schedule the CHUNK message
                        Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
                        ScheduledFuture<?> task = peer.getPool().schedule(new ChunkMessageSender(peer, msgToSend), new Random().nextInt(400), TimeUnit.MILLISECONDS);
                        peer.getMessagesToSend().put(key, task);
                    }
                } else if (message.getType() == MessageType.DELETE) {
                    peer.getChunks().forEach((key, value) -> {
                        if (key.startsWith(message.getFileId()) && peer.getChunks().remove(key, value)) {
                            File chunkToDelete = new File(key);
                            chunkToDelete.delete();
                        }
                    });
                } else if (message.getType() == MessageType.REMOVED) {
                    String key = message.getFileId() + "-" + message.getChunkNumber();
                    if (peer.getChunks().containsKey(key)) {
                        peer.getChunks().get(key).getPeers().remove(message.getSenderId());
                    }
                    //TODO: check if replication degree drops below desired
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
