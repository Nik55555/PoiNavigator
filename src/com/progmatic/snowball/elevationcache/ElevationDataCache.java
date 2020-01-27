package com.progmatic.snowball.elevationcache;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.progmatic.snowball.navigator.GeoPoint;
import com.progmatic.snowball.navigator.Parameters;
//import com.progmatic.snowball.service.Credentials;
//import com.progmatic.snowball.service.SbServiceServiceSoapBindingStub;
import com.progmatic.snowball.service.ServerProperties;
import com.progmatic.snowball.utils.ElevationData;
import com.progmatic.snowball.utils.TIFFStream;

/**
 * Altitude data cache.
 *
 * <p>Class with static public methods to get altitude data by given {@link GeoPoint}.</p>
 *
 * <p>Data stored in two-level caches.</p>
 *
 * <h4>First level or Memory cache.</h4>
 * <p>This is the place where lookup performed initially. Data is stored as array of short,
 * which is indexed by hgt file name. Number of stored arrays is limited by MAX_SIZE constant.</p>
 *
 * <h4>Second level or File cache.</h4>
 * <p>When the data is not found in Memory cache, then performed file lookup. Data is stored as
 * GZIP'ped hgt file in CacheDir directory. Number of stored files is unlimited.</p>
 *
 * <p>When data is absent in second level cache (file doesn't exists), it is downloaded via SOAP</p>
 */
public class ElevationDataCache {

    private static final String hgtFolderName = "";
    private static final String hgtGzExt = ".hgt.gz";
    private static final String hgtZipExt = ".zip";
    private static final File CacheDir = new File(getHomeFolder(), hgtFolderName);
    private static final File ZipCacheDir = new File(getHomeFolder(), getZipFolder());
    
	private static final int E6 = 1000000;


    
    static String getHomeFolder() {
    	return ServerProperties.GetProperty("ElevationDataPath");
    }
    
    static String getZipFolder() {
    	return ServerProperties.GetProperty("ASTGTM2Dir");
    }

    /**
     * Maximum number of Memory cache entries.
     * Each data array requires 2M of RAM. Limitation is required to avoid Out-of-memory issue.
     * Every time new Memory cache entry created, it's size is checked and most late used entry is
     * removed from cache. The array of removed entry is <b>reused</b>, filled with new data and
     * attached to new entry.
     */
    private static final int MAX_SIZE = 5;

    /**
     * Altitude cache entry.
     *
     * <p>Stores the name and data array. Synchronizes the access to <i>data</i> member. Load the
     * data on demand.</p>
     */
    private class Entry {
        /**
         * Entry name. The hgt file name. Duplicates the key of memory cache. Used to load the data
         * from File cache and to locate expired entry in cache during cache entry removal.
         */
        private final String name;

        /**
         * The altitude data. Array of short. {@see http://wiki.openstreetmap.org/wiki/SRTM#Format}
         */
        private short[] data;

        /**
         * Flag enabled when data was already requested and awaiting in some thread
         */
        private boolean alreadyRequested;

        /**
         * Constructs the cache Entry with given name.
         *
         * @param n
         *          the hgt file name
         */
        public Entry(String n)
        {
            name = n;
        }

        /**
         * Entry name accessor
         *
         * @return the hgt file name
         */
        public String getName()
        {
            return name;
        }

        /**
         * Entry alreadyRequested field accessor
         *
         * @return {@code true} if some other thread requested the data and still awaiting the result
         */
        public boolean getAlreadyRequested() { return alreadyRequested; }

        /**
         * Get the Entry data. If the data is absent, it is requested from File cache (and may
         * take a while). This method is thread-safe. In case of long download operation, all
         * callers are blocked.
         *
         * @param hint
         *            Optional array from expired (and removed) entry. Used to avoid garbage
         *            collection and effectively reuse allocated memory.
         * @return previously cached of obtained data. {@code null} in case of error.
         */
        public short[] getData(short[] hint)
        {
            synchronized (this) {
                if (data != null) {
                    return data;
                } else {
                    alreadyRequested = true;
                    data = loadElevationData(name, hint);
                    alreadyRequested = false;
                    return data;
                }
            }
        }

        /**
         * Release the entry data. Called to prepare Entry for removal. Old data detached and
         * will be reused with other entry.
         *
         * @return previously assigned data (maybe {@code null})
         */
        public short[] releaseData()
        {
            synchronized (this) {
                short[] res = data;
                data = null;
                return res;
            }
        }

        /**
         * Ensure the data is in File cache. If the data is absent, it is requested from server (and may
         * take a while). This method is thread-safe. In case of long download operation, all
         * callers are blocked.
         *
         * @param download_if_not_exists
         *          {@code true} enables download missing data, {@code false} only check
         *          presence in File cache
         * @return cached file size or 0 in case of error.
         */
        public long cacheData(boolean download_if_not_exists)
        {
            if (download_if_not_exists) {
                // synchronize, if download needed
                synchronized (this) {
                    alreadyRequested = true;
                    long result = getElevationDataSize(name, true);
                    alreadyRequested = false;
                    return result;
                }
            } else {
                // run without synchronization otherwise
                return getElevationDataSize(name, false);
            }
        }
    }

    /**
     * Memory cache indexed by hgt file name
     */
    private final HashMap<String, Entry> cache = new HashMap<>();

    /**
     * Memory cache sorted by use time. When the entry is used, it is moved at the end of the list.
     */
    private final List<Entry> lruList = new LinkedList<>();

    private volatile static ElevationDataCache instance;

    /** Returns singleton class instance */
    public static ElevationDataCache getInstance() {
        if (instance == null) {
            synchronized (ElevationDataCache.class) {
                if (instance == null) {
                    instance = new ElevationDataCache();
                }
            }
        }
        return instance;
    }

    /**
     * Get the altitude data for area with given GeoPoint.
     *
     * <p>This method may block on network and local IO operation and should not be called in UI
     * thread. Use AsyncTask or other methods of asynchronous execution.</p>
     *
     * @param point
     *             coordinates of the point which altitude required
     * @return altitude data array for 1°×1° area which contains the {@param point}
     */
    public static short[] getElevationData(GeoPoint point) {
        return getInstance().getElevationData(getHgtFilename(point));
    }

    /**
     * Get altitude data for given hgt file name.
     *
     * <p>Maintains Memory cache, control it's size, synchronizes access from different threads.</p>
     *
     * <p>This method may block on network and local IO operation and should not be called in UI
     * thread. Use AsyncTask or other methods of asynchronous execution.</p>
     *
     * @param name
     *            the name of hgt file
     * @return altitude data array for 1°×1° area
     */
    public short[] getElevationData(String name) {
        Entry entry;
        // reuse old array
        short[] hint = null;
        synchronized (cache) {
            entry = cache.get(name);
            if (entry == null) {
                entry = new Entry(name);
                cache.put(name, entry);
            } else {
                // remove entry from LRU list
                lruList.remove(entry);
            }
            // keep cache size less than MAX_SIZE
            while (lruList.size() >= MAX_SIZE) {
                // remove entry from the beginning of LRU list
                entry = lruList.remove(0);
                if (hint == null) {
                    hint = entry.releaseData();
                }
                cache.remove(entry.getName());
            }
            // insert at the end of the LRU list
            lruList.add(entry);
        }
        return entry.getData(hint);
    }

    /**
     * Get elevation data size for area with given GeoPoint.
     *
     * <p>Call this method to ensure the elevation data is in File cache and get the file size.
     * Main use case is Offline Maps downloading.</p>
     *
     * <p>This method may block on network and local IO operation and should not be called in UI
     * thread. Use AsyncTask or other methods of asynchronous execution.</p>
     *
     * @param point
     *             coordinates of the point which altitude required
     * @param download_if_not_exists
     *            {@code true} enables download missing data, {@code false} only check
     *            presence in File cache
     * @return elevation data size on disk
     */
    public static long cacheElevationData(GeoPoint point, boolean download_if_not_exists) {
        return getInstance().cacheElevationData(getHgtFilename(point), download_if_not_exists);
    }

    /**
     * Get elevation data size for given hgt file name.
     *
     * <p>Access to File cache should be performed through Memory cache for synchronization.
     * This method is optimized version of getElevationDataByName() aimed to ensure elevation data
     * is in File cache. Optimized operations are buffer allocation/reuse and data ungzipping.</p>
     *
     * <p>This method may block on network and local IO operation and should not be called in UI
     * thread. Use AsyncTask or other methods of asynchronous execution.</p>
     *
     * @param name
     *            the name of hgt file
     * @param download_if_not_exists
     *            {@code true} enables download missing data, {@code false} only check
     *            presence in File cache
     * @return gzip'ped data size
     */
    public long cacheElevationData(String name, boolean download_if_not_exists) {
        Entry entry;
        synchronized (cache) {
            entry = cache.get(name);
            if (entry == null) {
                entry = new Entry(name);
                cache.put(name, entry);
            }
            // do not affect the LRU
        }
        return entry.cacheData(download_if_not_exists);
    }

    public boolean getAlreadyRequested(String name) {
        Entry entry;
        synchronized (cache) {
            entry = cache.get(name);
            if (entry == null) {
                return false;
            }
        }
        return entry.getAlreadyRequested();
    }

    /**
     * Constructs hgt file name based on point coordinates.
     *
     * @param point
     *             input coordinates
     * @return the name of hgt file
     */
    public static String getHgtFilename(GeoPoint point) {
        char la = point.getLatitude() > 0 ? 'N' : 'S';
        char lo = point.getLongitude() < 0 ? 'W' : 'E';
        int lat_int = Math.abs((int)Math.floor(point.getLatitude()));
        int lon_int = Math.abs((int)Math.floor(point.getLongitude()));
        return String.format("%c%02d%c%03d", la, lat_int, lo, lon_int);
    }

    /**
     * Default protected constructor. Use getInstance() for instance creation/access.
     */
    protected ElevationDataCache() {
    }

    /**
     * Utility method to read GZip'ped stream into memory.
     *
     * @param source
     *              input GZip'ped stream
     * @param hint
     *            if not {@code null}, used to put altitude data and return
     * @return the altitude data
     */
    private static short[] readHgtFromStream(InputStream source, short[] hint)
    {
        DataInputStream in = null;
        ZipInputStream zis;
        try {
            short[] result;
            // check if hint is not null and has required length
            if (hint != null && hint.length == Parameters.hgtRows * Parameters.hgtColumns) {
                result = hint;
            } else {
                // allocate new array, if hint cannot be used
                result = new short[Parameters.hgtRows * Parameters.hgtColumns];
            }
            // temporary byte buffer, used to minimize read() and readFully() calls
            byte[] buffer = new byte[Parameters.hgtColumns * 2];

            if (source.markSupported()) {
                source.mark(2);
                if (source.read(buffer, 0, 2) != 2) {
                    // failed to read signature
                    return null;
                }
                source.reset();
            } else {
                buffer[0] = 0x1f;
                buffer[1] = (byte)0x8b;
            }
            // create DataInputStream, which provides readFully() method
            if (buffer[0] == 0x1f && (buffer[1] & 0xff) == 0x8b) {
                in = new DataInputStream(new GZIPInputStream(source));
            } else
            if (buffer[0] == 0x50 && buffer[1] == 0x4b) {
/*                zis = new ZipInputStream(source);
                zis.getNextEntry();
                in = new DataInputStream(zis);
*/

        		ElevationData hgt = new ElevationData();

        		ElevationData srcHgt = new ElevationData(hgt);
				srcHgt.inflate(E6, E6);
            
				zis = new ZipInputStream(source);
				ZipEntry entry;
				while( (entry = zis.getNextEntry()) != null ) {
					// lookup Digital Elevation Model file
					if (!entry.getName().endsWith("_dem.tif")) continue;

					try {
						TIFFStream.readTIFF(srcHgt, zis, (int)entry.getSize());
					} catch (Exception e) {
						return null;
					}
					finally{
						zis.close();
					}
					break;
				}
				
	            int i = 0;
	            for(int y = 0; y < Parameters.hgtRows; ++y) {
	                for(int x = 0; x < Parameters.hgtColumns; ++x) {
	                    // fill result with 16bit BIG-endian value from byte buffer
	                    result[i] = (short)((srcHgt.data[x * 2] << 8) + (buffer[x * 2 + 1] & 0xFF));
	                    ++i;
	                }
	            }

				zis.close();
				return result;
            } else {
                // unexpected signature
                return null;
            }

            int i = 0;
            for(int y = 0; y < Parameters.hgtRows; ++y) {
                in.readFully(buffer);
                for(int x = 0; x < Parameters.hgtColumns; ++x) {
                    // fill result with 16bit BIG-endian value from byte buffer
                    result[i] = (short)((buffer[x * 2] << 8) + (buffer[x * 2 + 1] & 0xFF));
                    ++i;
                }
            }
            
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // always good idea to put close() in finally
            if (in != null) {
                // unfortunately, close() may throw() ...
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // no luck case ..
        return null;
    }

    /**
     * Load altitude data from File cache or remote server.
     *
     * @param name
     *            the name of hgt file
     * @param hint
     *            optional. place where to put altitude data
     * @return the altitude data or {@code null} in case of error
     */
    private static short[] loadElevationData(String name, short[] hint) {
        short[] result = null;
        try {
            InputStream is;
            File gzFile = new File(CacheDir, name + hgtGzExt);
            File zipFile = null;
            if (gzFile.exists() && gzFile.length() != 0) {
                // when we load GZip'ped data from file, no need to load whole file in memory at once
                is = new BufferedInputStream(new FileInputStream(gzFile));
            } else {
                zipFile = new File(ZipCacheDir, "ASTGTM2_" + name + hgtZipExt);
                if (zipFile.exists() && zipFile.length() != 0) {
                    is = new BufferedInputStream(new FileInputStream(zipFile));
                } else {
                    //byte[] data = downloadElevationData(name, gzFile);
                    //is = new ByteArrayInputStream(data);
                	throw new Exception("File " + zipFile.getAbsolutePath() + "does not exist.");
                }
            }

            result = readHgtFromStream(is, hint);

            if (result == null) {
                // the file may contain corrupted data, let's remove it.
                gzFile.delete();
                if (zipFile != null) {
                   // zipFile.delete();
                }
                is.close();
                throw new Exception("File " + name + " corrupted");
            }
            
            is.close();
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        
        return result;
    }

    /**
     * Load altitude data from remote server.
     *
     * @param name
     *            the name of hgt file
     * @param file
     *            local file to store
     * @return the altitude data or {@code null} in case of error
     */
    /*private static byte[] downloadElevationData(String name, File file) throws Exception {
    	SbServiceServiceSoapBindingStub service = new SbServiceServiceSoapBindingStub(new URL(Parameters.serviceUrl), null);
	    Credentials credentials = new Credentials("System", "Snowball");
		byte [] data = service.getElevationData(credentials, name);

        if (data == null) {
            throw new Exception("No elevation data " + name);
        }

        if (!CacheDir.isDirectory() && !CacheDir.mkdirs()) {
            throw new Exception("Failed to create cache directory");
        }

        OutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();

        return data;
    }*/

    /**
     * Get altitude data file size. If no file found, downloads it from remote server.
     *
     * @param name
     *            the name of hgt file
     * @param download_if_not_exists
     *            enables download of missing data
     * @return data file size
     */
    private static long getElevationDataSize(String name, boolean download_if_not_exists) {
        long result = 0;
        try {
            File gzFile = new File(CacheDir, name + hgtGzExt);
            if (gzFile.exists() && gzFile.length() != 0) {
                return gzFile.length();
            }
            File zipFile = new File(CacheDir, name + hgtZipExt);
            if (zipFile.exists() && zipFile.length() != 0) {
                return zipFile.length();
            }
            if (download_if_not_exists) {
                //byte[] data = downloadElevationData(name, gzFile);
                //result = data.length;
            	throw new Exception("File " + name + "does not exist.");
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return result;
    }
}
