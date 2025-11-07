package net.runelite.client.plugins.multiboxer;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages inter-process communication using memory-mapped files
 * No server needed - all clients read/write to same file in memory
 */
@Slf4j
@Singleton
public class SharedMemoryManager
{
	@Inject
	private MultiboxerPlugin plugin;

	private final Gson gson = new Gson();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	private volatile boolean running = false;
	private File sharedFile;
	private RandomAccessFile raf;
	private MappedByteBuffer readBuffer;
	private AtomicLong lastReadPosition = new AtomicLong(0);

	// File structure:
	// [8 bytes: write position]
	// [N bytes: JSON messages, each prefixed with 4-byte length]
	private static final int HEADER_SIZE = 8;
	private static final int FILE_SIZE = 1024 * 1024; // 1MB circular buffer

	/**
	 * Start the shared memory manager
	 */
	public void start(String filePath)
	{
		if (running)
		{
			log.warn("Shared memory manager already running");
			return;
		}

		try
		{
			sharedFile = new File(filePath);

			// Create parent directories if needed
			File parentDir = sharedFile.getParentFile();
			if (parentDir != null && !parentDir.exists())
			{
				parentDir.mkdirs();
			}

			// Create/open the shared file
			raf = new RandomAccessFile(sharedFile, "rw");

			// Set file size
			if (raf.length() < FILE_SIZE)
			{
				raf.setLength(FILE_SIZE);
			}

			// Map file to memory for reading
			readBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, FILE_SIZE);

			running = true;

			// Start background thread to poll for new messages
			executorService.submit(this::pollMessages);

			log.info("Shared memory manager started: {}", filePath);
		}
		catch (Exception e)
		{
			log.error("Failed to start shared memory manager", e);
		}
	}

	/**
	 * Stop the shared memory manager
	 */
	public void stop()
	{
		running = false;

		try
		{
			if (raf != null)
			{
				raf.close();
			}
		}
		catch (Exception e)
		{
			log.error("Error closing shared memory", e);
		}

		executorService.shutdown();
	}

	/**
	 * Write an action to shared memory
	 */
	public void writeAction(ActionMessage action)
	{
		try
		{
			String json = gson.toJson(action);
			byte[] data = json.getBytes(StandardCharsets.UTF_8);

			synchronized (this)
			{
				// Get current write position
				readBuffer.position(0);
				long writePos = readBuffer.getLong();

				// Calculate actual position in circular buffer
				int bufferPos = (int)((writePos % (FILE_SIZE - HEADER_SIZE)) + HEADER_SIZE);

				// Write length prefix + data
				readBuffer.position(bufferPos);
				readBuffer.putInt(data.length);
				readBuffer.put(data);

				// Update write position in header
				readBuffer.position(0);
				readBuffer.putLong(writePos + 4 + data.length);

				// Force changes to disk (ensures other processes see it)
				readBuffer.force();
			}
		}
		catch (Exception e)
		{
			log.error("Error writing to shared memory", e);
		}
	}

	/**
	 * Poll for new messages in background thread
	 */
	private void pollMessages()
	{
		while (running)
		{
			try
			{
				// Check if there are new messages
				readBuffer.position(0);
				long currentWritePos = readBuffer.getLong();
				long lastRead = lastReadPosition.get();

				if (currentWritePos > lastRead)
				{
					// Calculate position to read from
					int bufferPos = (int)((lastRead % (FILE_SIZE - HEADER_SIZE)) + HEADER_SIZE);

					// Read message length
					readBuffer.position(bufferPos);
					int messageLength = readBuffer.getInt();

					// Sanity check
					if (messageLength > 0 && messageLength < FILE_SIZE)
					{
						// Read message data
						byte[] data = new byte[messageLength];
						readBuffer.get(data);

						// Parse and handle message
						String json = new String(data, StandardCharsets.UTF_8);
						ActionMessage action = gson.fromJson(json, ActionMessage.class);

						// Update last read position
						lastReadPosition.set(lastRead + 4 + messageLength);

						// Handle the action
						if (action != null)
						{
							plugin.handleRemoteAction(action);
						}
					}
				}

				// Sleep briefly to avoid busy-waiting
				Thread.sleep(10); // Check every 10ms
			}
			catch (Exception e)
			{
				if (running) // Only log if we're supposed to be running
				{
					log.error("Error polling shared memory", e);
				}
			}
		}
	}

	/**
	 * Send an action to shared memory
	 */
	public void sendAction(ActionMessage action)
	{
		writeAction(action);
	}

	/**
	 * Send a prayer sync message
	 */
	public void sendPrayerSync(PrayerSyncMessage prayerMsg)
	{
		// For now, we'll just log this - can implement later if needed
		log.debug("Prayer sync not yet implemented in shared memory mode");
	}

	/**
	 * Send a special attack sync message
	 */
	public void sendSpecialAttack(SpecialAttackMessage specMsg)
	{
		// For now, we'll just log this - can implement later if needed
		log.debug("Special attack sync not yet implemented in shared memory mode");
	}

	/**
	 * Send a combat style sync message
	 */
	public void sendCombatStyle(CombatStyleMessage styleMsg)
	{
		// For now, we'll just log this - can implement later if needed
		log.debug("Combat style sync not yet implemented in shared memory mode");
	}
}
