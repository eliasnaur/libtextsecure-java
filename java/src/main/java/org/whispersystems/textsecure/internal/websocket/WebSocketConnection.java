package org.whispersystems.textsecure.internal.websocket;

import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.logging.Log;
import org.whispersystems.textsecure.api.TextSecureMessagePipe.MessagePipeCallback;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.util.CredentialsProvider;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketMessage;
import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

public class WebSocketConnection implements WebSocketEventListener {

  private static final String TAG                       = WebSocketConnection.class.getSimpleName();
  private static final int    KEEPALIVE_TIMEOUT_SECONDS = 55;

  private final LinkedList<WebSocketRequestMessage> incomingRequests = new LinkedList<>();

  private final String              wsUri;
  private final TrustStore          trustStore;
  private final CredentialsProvider credentialsProvider;
  private final int					readTimeoutSeconds;

  private OkHttpClientWrapper client;
  private KeepAliveSender     keepAliveSender;

  public WebSocketConnection(String httpUri, TrustStore trustStore, CredentialsProvider credentialsProvider, int readTimeoutSeconds) {
	this.readTimeoutSeconds = readTimeoutSeconds;
    this.trustStore          = trustStore;
    this.credentialsProvider = credentialsProvider;
    this.wsUri               = httpUri.replace("https://", "wss://")
                                      .replace("http://", "ws://") + "/v1/websocket/?login=%s&password=%s";
  }

  public synchronized void connect() throws IOException {
    Log.w(TAG, "WSC connect()...");

    if (client == null) {
      client = new OkHttpClientWrapper(wsUri, trustStore, credentialsProvider, this);
      client.connect(KEEPALIVE_TIMEOUT_SECONDS + 10, readTimeoutSeconds, TimeUnit.SECONDS);
    }
  }

  public synchronized void disconnect() {
    Log.w(TAG, "WSC disconnect()...");

    if (client != null) {
      client.disconnect();
      client = null;
    }

    if (keepAliveSender != null) {
      keepAliveSender.shutdown();
      keepAliveSender = null;
    }
  }

  public synchronized WebSocketRequestMessage readRequest(long timeoutMillis, MessagePipeCallback callback)
      throws TimeoutException, IOException
  {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    long startTime = System.currentTimeMillis();

    while (client != null && incomingRequests.isEmpty() && elapsedTime(startTime) < timeoutMillis) {
		boolean sleep = keepAliveSender != null;
		if (sleep)
			callback.sleep();
		try {
      Util.wait(this, Math.max(1, timeoutMillis - elapsedTime(startTime)));
		} finally {
			if (sleep)
				callback.wakeup();
		}
    }

    if      (incomingRequests.isEmpty() && client == null) throw new IOException("Connection closed!");
    else if (incomingRequests.isEmpty())                   throw new TimeoutException("Timeout exceeded");
    else                                                   return incomingRequests.removeFirst();
  }

  public synchronized void sendResponse(WebSocketResponseMessage response) throws IOException {
    if (client == null) {
      throw new IOException("Connection closed!");
    }

    WebSocketMessage message = WebSocketMessage.newBuilder()
                                               .setType(WebSocketMessage.Type.RESPONSE)
                                               .setResponse(response)
                                               .build();

    client.sendMessage(message.toByteArray());
  }

  public synchronized void sendKeepAlive() throws IOException {
    if (keepAliveSender != null) {
      client.sendMessage(WebSocketMessage.newBuilder()
                                         .setType(WebSocketMessage.Type.REQUEST)
                                         .setRequest(WebSocketRequestMessage.newBuilder()
                                                                            .setId(System.currentTimeMillis())
                                                                            .setPath("/v1/keepalive")
                                                                            .setVerb("GET")
                                                                            .build()).build()
                                         .toByteArray());
    }
  }

  public synchronized void onMessage(byte[] payload) {
    Log.w(TAG, "WSC onMessage()");
    try {
      WebSocketMessage message = WebSocketMessage.parseFrom(payload);

      Log.w(TAG, "Message Type: " + message.getType().getNumber());

      if (message.getType().getNumber() == WebSocketMessage.Type.REQUEST_VALUE)  {
        incomingRequests.add(message.getRequest());
      }

      notifyAll();
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, e);
    }
  }

  public synchronized void onClose() {
    Log.w(TAG, "onClose()...");

    if (client != null) {
      client.disconnect();
      client = null;
      //connect();
    }

    if (keepAliveSender != null) {
      keepAliveSender.shutdown();
      keepAliveSender = null;
    }

    notifyAll();
  }

  public synchronized void onConnected() {
    if (client != null && keepAliveSender == null) {
      Log.w(TAG, "onConnected()");
      keepAliveSender = new KeepAliveSender();
	  notifyAll();
      //keepAliveSender.start();
    }
  }

  private long elapsedTime(long startTime) {
    return System.currentTimeMillis() - startTime;
  }

  private class KeepAliveSender extends Thread {

    private AtomicBoolean stop = new AtomicBoolean(false);

    public void run() {
      while (!stop.get()) {
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(KEEPALIVE_TIMEOUT_SECONDS));

          Log.w(TAG, "Sending keep alive...");
          sendKeepAlive();
        } catch (Throwable e) {
          Log.w(TAG, e);
        }
      }
    }

    public void shutdown() {
      stop.set(true);
    }
  }
}
