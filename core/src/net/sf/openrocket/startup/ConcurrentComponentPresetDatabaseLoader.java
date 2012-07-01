package net.sf.openrocket.startup;

import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.sf.openrocket.database.ComponentPresetDatabase;
import net.sf.openrocket.file.iterator.DirectoryIterator;
import net.sf.openrocket.file.iterator.FileIterator;
import net.sf.openrocket.gui.util.SimpleFileFilter;
import net.sf.openrocket.logging.LogHelper;
import net.sf.openrocket.preset.ComponentPreset;
import net.sf.openrocket.preset.xml.OpenRocketComponentLoader;
import net.sf.openrocket.util.Pair;

public class ConcurrentComponentPresetDatabaseLoader {

	private static final LogHelper log = Application.getLogger();
	private static final String SYSTEM_PRESET_DIR = "datafiles/presets";

	private final CountDownLatch latch = new CountDownLatch(1);

	private final ComponentPresetDatabase componentPresetDao;

	private final ExecutorService writerPool;

	private final ExecutorService loaderPool;

	private final Thread workGenerator;

	private FileIterator iterator;

	private long startTime;
	private long fileCount = 0;
	private long presetCount = 0;

	ConcurrentComponentPresetDatabaseLoader( ComponentPresetDatabase componentPresetDao ) {
		this.componentPresetDao = componentPresetDao;

		writerPool = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r,"PresetWriterThread");
				return t;
			}
		});

		loaderPool = Executors.newFixedThreadPool(15, new ThreadFactory() {
			int threadCount = 0;
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r,"PresetLoaderPool-" + threadCount++);
				return t;
			}

		});

		workGenerator = new Thread( new WorkGenerator(),"PresetGeneratorThread");
	}

	public void load() {
		startTime = System.currentTimeMillis();
		workGenerator.start();
	}

	public void await() throws InterruptedException {
		latch.await();
		loaderPool.shutdown();
		loaderPool.awaitTermination(10, TimeUnit.SECONDS);
		writerPool.shutdown();
		writerPool.awaitTermination(10, TimeUnit.SECONDS);
		iterator.close();
		long end = System.currentTimeMillis();
		log.debug("Time to load presets: " + (end-startTime) + "ms " + presetCount + " loaded from " + fileCount + " files");
	}


	private class WorkGenerator implements Runnable {
		@Override
		public void run() {
			// Start loading
			log.info("Loading component presets from " + SYSTEM_PRESET_DIR);

			iterator = DirectoryIterator.findDirectory(SYSTEM_PRESET_DIR,
					new SimpleFileFilter("", false, "orc"));

			if (iterator == null) {
				throw new IllegalStateException("Component preset directory " + SYSTEM_PRESET_DIR +
						" not found, distribution built wrong");
			}

			while( iterator.hasNext() ) {
				Pair<String,InputStream> f = iterator.next();
				FileLoader loader = new FileLoader( f.getV(), f.getU() );
				loaderPool.execute(loader);
				fileCount ++;
			}

			latch.countDown();
		}
	}

	private class FileLoader implements Runnable {
		private final InputStream is;
		private final String fileName;

		public FileLoader(InputStream is, String fileName) {
			super();
			this.is = is;
			this.fileName = fileName;
		}

		@Override
		public void run() {
			OpenRocketComponentLoader loader = new OpenRocketComponentLoader();
			Collection<ComponentPreset> presets = loader.load(is, fileName);
			PresetWriter writer = new PresetWriter(presets);
			writerPool.execute(writer);
		}		
	}

	private class PresetWriter implements Runnable {
		private final Collection<ComponentPreset> presets;

		public PresetWriter(Collection<ComponentPreset> presets) {
			super();
			this.presets = presets;
		}

		@Override
		public void run() {
			presetCount += presets.size();
			componentPresetDao.addAll(presets);
		}

	}
}
