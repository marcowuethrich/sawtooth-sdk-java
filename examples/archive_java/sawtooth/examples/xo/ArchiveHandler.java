package sawtooth.examples.xo;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.google.protobuf.ByteString;
import sawtooth.sdk.processor.Context;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.Utils;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

enum TransactionAction {
	CREATE("create"), REPLACE("replace");

	public String action;

	TransactionAction(String action) {
		this.action = action;
	}

	public String getValue() {
		return action;
	}
}

enum RecordKey {
	CONTENT("content"), DIP("dip");
	private final String key;

	RecordKey(String value) {
		this.key = value;
	}

	public String getKey() {
		return key;
	}
}

public class ArchiveHandler implements TransactionHandler {

	private final Logger logger = Logger.getLogger(ArchiveHandler.class.getName());
	private final String xoNameSpace;

	/**
	 * constructor.
	 */
	public ArchiveHandler() {
		this.xoNameSpace = Utils.hash512(this.transactionFamilyName().getBytes(StandardCharsets.UTF_8)).substring(0, 6);
	}

	@Override
	public String transactionFamilyName() {
		return "archive";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public Collection<String> getNameSpaces() {
		ArrayList<String> namespaces = new ArrayList<>();
		namespaces.add(this.xoNameSpace);
		return namespaces;
	}

	@Override
	public void apply(TpProcessRequest transactionRequest, Context context)
			throws InvalidTransactionException, InternalError {
		TransactionData transactionData = getUnpackedTransaction(transactionRequest);

		if (transactionData.refAddress.equals("")) {
			throw new InvalidTransactionException("RefAddress is required");
		}
		if (transactionData.contentHash.equals("")) {
			throw new InvalidTransactionException("ContentHash is required");
		}
		if (transactionData.dipHash.equals("")) {
			throw new InvalidTransactionException("dipHash is required");
		}
		if (!transactionData.action.equals(TransactionAction.CREATE.getValue())
				&& !transactionData.action.equals(TransactionAction.REPLACE.getValue())) {
			throw new InvalidTransactionException(String.format("Invalid action: %s", transactionData.action));
		}

		String address = makeArchiveRecordAddress(transactionData.refAddress);
		ArchiveRecord stateRecord = this.decodePayload(context, address);
		ArchiveRecord updatedRecord = executeTransactionLogic(transactionData, stateRecord);
		storeArchiveRecord(address, updatedRecord, context);
	}

	private ArchiveRecord decodePayload(Context context, String address)
			throws InternalError, InvalidTransactionException {
		// context.get() returns a list.
		// If no data has been stored yet at the given address, it will be empty.
		byte[] stateValueRep = context.getState(Collections.singletonList(address)).get(address).toByteArray();
		if (stateValueRep.length == 0) {
			throw new InvalidTransactionException("No payload found on address: " + address);
		}
		try {
			Map<String, String> stateValue = this.decodePayload(stateValueRep);
			return new ArchiveRecord(stateValue.get(RecordKey.CONTENT.getKey()), stateValue.get(RecordKey.DIP.getKey()));
		} catch (CborException e) {
			e.printStackTrace();
			throw new InvalidTransactionException(e.getMessage());
		}
	}

	/**
	 * Helper function to decode the Payload of a transaction.
	 * Convert the co.nstant.in.cbor.model.Map to a HashMap.
	 */
	private Map<String, String> decodePayload(byte[] bytes) throws CborException {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		co.nstant.in.cbor.model.Map data = (co.nstant.in.cbor.model.Map) new CborDecoder(bais).decodeNext();
		DataItem[] keys = data.getKeys().toArray(new DataItem[0]);
		HashMap<String, String> result = new HashMap<>();
		for (DataItem key : keys) {
			result.put(
					key.toString(),
					data.get(key).toString());
		}
		return result;
	}

	/**
	 * Helper function to encode the State that will be stored at the address of
	 * the ArchiveRecord.
	 */
	public Map.Entry<String, ByteString> encodeState(String address, ArchiveRecord archiveRecord) throws CborException {
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		new CborEncoder(boas).encode(new CborBuilder()
				.addMap()
				.put(RecordKey.CONTENT.getKey(), archiveRecord.contentHash)
				.put(RecordKey.DIP.getKey(), archiveRecord.dipHash)
				.end()
				.build());
		return new AbstractMap.SimpleEntry<>(address, ByteString.copyFrom(boas.toByteArray()));
	}

	/**
	 * Helper function to retrieve game gameName, action, and space from transaction request.
	 */
	private TransactionData getUnpackedTransaction(TpProcessRequest transactionRequest)
			throws InvalidTransactionException {
		String payload = transactionRequest.getPayload().toStringUtf8();
		ArrayList<String> payloadList = new ArrayList<>(Arrays.asList(payload.split(",")));
		if (payloadList.size() != 4) {
			throw new InvalidTransactionException("Invalid payload serialization");
		}
		return new TransactionData(payloadList.get(0), payloadList.get(1), payloadList.get(2), payloadList.get(3));
	}

	/**
	 * Helper function to generate archive record address.
	 */
	private String makeArchiveRecordAddress(String recordId) {
		return xoNameSpace + recordId.substring(0, 64);
	}

	/**
	 * Helper function to store state data.
	 */
	private void storeArchiveRecord(String address, ArchiveRecord archiveRecord, Context context)
			throws InternalError, InvalidTransactionException {

		Map.Entry<String, ByteString> entry;
		try {
			entry = this.encodeState(address, archiveRecord);
		} catch (CborException e) {
			e.printStackTrace();
			throw new InternalError("Failed to encode archive data");
		}
		Collection<Map.Entry<String, ByteString>> addressValues = Collections.singletonList(entry);
		Collection<String> addresses = context.setState(addressValues);

		if (addresses.size() == 0) {
			throw new InternalError("State error!.");
		}

	}

	/**
	 * Function that handles archive logic.
	 */
	private ArchiveRecord executeTransactionLogic(TransactionData transactionData, ArchiveRecord archiveRecord)
			throws InvalidTransactionException {
		switch (transactionData.action) {
			case "create": // TransactionAction.CREATE.getValue()
				return applyCreate(transactionData);
			case "replace": // TransactionAction.REPLACE.getValue()
				return applyReplace(transactionData, archiveRecord);
			default:
				throw new InvalidTransactionException(String.format("Invalid action: %s", transactionData.action));
		}
	}

	/**
	 * Function that handles archive logic for 'create' action.
	 */
	private ArchiveRecord applyCreate(TransactionData transactionData) {
		ArchiveRecord record = new ArchiveRecord(transactionData.contentHash, transactionData.dipHash);
		logger.info(String.format("Action: %s, new archive record with data: content: %s, sip: %s",
				transactionData.action, record.contentHash, record.dipHash));
		return record;
	}

	/**
	 * Function that handles archive logic for 'replace' action.
	 */
	private ArchiveRecord applyReplace(TransactionData transactionData, ArchiveRecord archiveRecord) {
		ArchiveRecord record = new ArchiveRecord(transactionData.contentHash, transactionData.dipHash);
		logger.info(String.format("Action: %s, modify archive record with data:", transactionData.action));
		logger.info(String.format("content: %s ->: %s", archiveRecord.contentHash, record.contentHash));
		logger.info(String.format("sip: %s ->: %s", archiveRecord.dipHash, record.dipHash));
		return record;
	}

	static class TransactionData {
		final String refAddress;
		final String action;
		final String contentHash;
		final String dipHash;

		public TransactionData(String refAddress, String action, String contentHash, String dipHash) {
			this.refAddress = refAddress;
			this.action = action;
			this.contentHash = contentHash;
			this.dipHash = dipHash;
		}
	}

	static class ArchiveRecord {
		final String contentHash;
		final String dipHash;

		public ArchiveRecord(String contentHash, String dipHash) {
			this.contentHash = contentHash;
			this.dipHash = dipHash;
		}
	}
}
