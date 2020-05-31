package sawtooth.examples.xo;

import sawtooth.sdk.processor.TransactionProcessor;

public class ArchiveTransactionProcessor {
  /**
   * the method that runs a Thread with a TransactionProcessor in it.
   */
  public static void main(String[] args) {
    TransactionProcessor transactionProcessor = new TransactionProcessor(args[0]);
    transactionProcessor.addHandler(new ArchiveHandler());
    Thread thread = new Thread(transactionProcessor);
    thread.start();
  }
}
