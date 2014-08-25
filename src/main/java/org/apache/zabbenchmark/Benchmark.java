package org.apache.zabbenchmark;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import org.apache.zab.QuorumZab;
import org.apache.zab.StateMachine;
import org.apache.zab.Zxid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmark.
 */
public class Benchmark implements StateMachine {

  private static final Logger LOG = LoggerFactory.getLogger(Benchmark.class);

  final QuorumZab zab;

  String serverId;

  private static final String CONFIG = "benchmark_config";

  int txnCount;

  int txnSize;

  int deliveredCount = 0;

  int membersCount = 0;

  int stateMemory = 0;

  CountDownLatch condFinish = new CountDownLatch(1);

  CountDownLatch condMembers = new CountDownLatch(1);

  CountDownLatch condBroadcasting = new CountDownLatch(1);

  State currentState = null;

  ConcurrentHashMap<Integer, String> state = new ConcurrentHashMap<>();

  enum State {
    LEADING,
    FOLLOWING
  }

  public Benchmark() {
    LOG.debug("Benchmark.");
    try {
      String selfId = System.getProperty("serverId");
      String logDir = System.getProperty("logdir");
      String joinPeer = System.getProperty("join");
      if (selfId != null && joinPeer == null) {
        joinPeer = selfId;
      }
      Properties prop = new Properties();
      if (selfId != null) {
        prop.setProperty("serverId", selfId);
        prop.setProperty("logdir", selfId);
      }
      if (logDir != null) {
        prop.setProperty("logdir", logDir);
      }
      prop.setProperty("timeout_ms", "200000");
      zab = new QuorumZab(this, prop, joinPeer);
      this.serverId = zab.getServerId();
    } catch (Exception ex) {
      LOG.error("Caught exception : ", ex);
      throw new RuntimeException();
    }
  }

  @Override
  public void save(OutputStream os) {
    LOG.info("SAVE is called.");
    try {
      ObjectOutputStream out = new ObjectOutputStream(os);
      out.writeObject(state);
    } catch (IOException e) {
      LOG.error("Caught exception", e);
    }
  }

  @Override
  public void restore(InputStream is) {
    LOG.info("RESTORE is called.");
    try {
      ObjectInputStream oin = new ObjectInputStream(is);
      state = (ConcurrentHashMap<Integer, String>)oin.readObject();
      LOG.info("The size of map after recovery from snapshot file is {}",
                state.size());
    } catch (Exception e) {
      LOG.error("Caught exception", e);
    }
  }

  @Override
  public void deliver(Zxid zxid, ByteBuffer stateUpdate, String clientId) {
    this.deliveredCount++;
    byte[] bytes = new byte[stateUpdate.remaining()];
    stateUpdate.get(bytes);
    state.put(deliveredCount % state.size(), new String(bytes));
    if (this.deliveredCount == this.txnCount) {
      this.condFinish.countDown();
    }
  }

  @Override
  public ByteBuffer preprocess(Zxid zxid, ByteBuffer message) {
    return message;
  }

  @Override
  public void leading(Set<String> activeFollowers, Set<String> members) {
    this.currentState = State.LEADING;
    this.condBroadcasting.countDown();
    LOG.info("Cluster member size : {}", members.size());
    if (members.size() == this.membersCount) {
      this.condMembers.countDown();
    }
  }

  @Override
  public void following(String leader, Set<String> members) {
    this.currentState = State.FOLLOWING;
    this.condBroadcasting.countDown();
    LOG.info("Cluster member size : {}", members.size());
    if (members.size() == this.membersCount) {
      this.condMembers.countDown();
    }
  }

  @Override
  public void recovering() {
  }

  public void start() throws IOException, InterruptedException {
    Properties prop = new Properties();
    try (FileInputStream fin = new FileInputStream(CONFIG)) {
      prop.load(fin);
    } catch (FileNotFoundException ex) {
      LOG.warn("Can't find benchmark_config file, use default config.");
    }
    this.membersCount = Integer.parseInt(prop.getProperty("membersCount", "1"));
    this.txnSize = Integer.parseInt(prop.getProperty("txnSize", "128"));
    this.txnCount = Integer.parseInt(prop.getProperty("txnCount", "1000000"));
    this.stateMemory =
      Integer.parseInt(prop.getProperty("stateMemory", "1000000"));
    initState();
    LOG.info("Benchmark begins : txnSize {}, txnCount : {}, membersCount : {}",
             txnSize, this.txnCount, this.membersCount);
    this.condBroadcasting.await();
    long startNs;
    if (this.currentState == State.LEADING) {
      LOG.info("It's leading.");
      LOG.info("Waiting for member size changes to {}", this.membersCount);
      this.condMembers.await();
      startNs = System.nanoTime();
      String message = new String(new char[txnSize]).replace('\0', 'a');
      for (int i = 0; i < this.txnCount; ++i) {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        this.zab.send(buffer);
      }
    } else {
      this.condMembers.await();
      startNs = System.nanoTime();
    }
    this.condFinish.await();
    long endNs = System.nanoTime();
    double duration = ((double)(endNs - startNs)) / 1000000000;
    LOG.info("Benchmark finished.");
    LOG.info("Duration : {} s", duration);
    LOG.info("Throughput : {} txns/s", this.txnCount / duration);
    this.zab.shutdown();
  }

  void initState() {
    LOG.info("Initializing the state.");
    int numKeys = this.stateMemory / this.txnSize;
    String value = new String(new char[txnSize]).replace('\0', 'a');
    for (int i = 0; i < numKeys; ++i) {
      state.put(i, value);
    }
    LOG.info("After initialize the memory, the state has size {}",
             state.size());
  }
}
