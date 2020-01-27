package com.progmatic.snowball.utils.dem_generator;

import java.io.File;

/**
 * A collection of helper methods to generate DEM model properly
 * @author Leon Dobnik
 */
public final class DemGeneratorUtils {
  
  /**
   * Template for formating latitude directory
   */
  private static final String LATITUDE_DIRECTORY_NAME_TEMPLATE = "%s%02d";
  
  /**
   * Template for  formatting longitude directory
   */
  private static final String LONGITUDE_DIRECTORY_NAME_TEMPLATE = "%s%03d";
  
  /**
   * Prevents construction of instances
   */
  private DemGeneratorUtils() {};
  
     /**
   * Produces name of longitude directory in which hgt tile slices are stores. The directory s combined from
   * W (west) or E(east) and three digit number - for example E008, E010, 
   * @param longitude to generate longitude directory from
   * @return directory name as specified above
   */
  public static String produceLongitudeDirectoryName(double longitude) {
    return produceLatitudeDirectoryName((int)longitude);
  }
  
  /**
   * Produces name of longitude directory in which hgt tile slices are stores. The directory s combined from
   * W (west) or E(east) and three digit number - for example E008, E010, 
   * @param longitude to generate longitude directory from
   * @return directory name as specified above
   */
  public static String produceLongitudeDirectoryName(int longitude) {
    return String.format(LONGITUDE_DIRECTORY_NAME_TEMPLATE, longitudeToWestEast(longitude), Math.abs(longitude));
  }
  
  /**
   * Produces name of latitude directory in which hgt tile slices are stored. The directory is combined from
   * N (north) S (south) two digit latitude - for example N48, N08
   * @param latitude to generate directory name
   * @return directory name as specified above
   */
  public static String produceLatitudeDirectoryName(int latitude) {
    return String.format(LATITUDE_DIRECTORY_NAME_TEMPLATE, latitudeToNorthSouth(latitude), Math.abs(latitude));
  }
  
    /**
   * Return N or S for the specific latitude
   * @param latitude to return N or S character
   * @return S when latitude &lt; 0, and N otherwise
   */
  public static final String latitudeToNorthSouth(double latitude) {
    return latitudeToNorthSouth((int)latitude);
  }
  
  /**
   * Return N or S for the specific latitude
   * @param latitude to return N or S character
   * @return S when latitude &lt; 0, and N otherwise
   */
  public static final String latitudeToNorthSouth(int latitude) {
    return (latitude < 0) ? "S" : "N";
  }
  
  /**
   * Return W or E for the specified longitude
   * @param longitude to return E or W character
   * @return W when longitude &lt; 0 and E otherwise 
   */
  public static final String longitudeToWestEast(double longitude) {
    return longitudeToWestEast((int)longitude);
  }
  
  /**
   * Return W or E for the specified longitude
   * @param longitude to return E or W character
   * @return W when longitude &lt; 0 and E otherwise 
   */
  public static final String longitudeToWestEast(int longitude) {
    return (longitude < 0) ? "W" : "E";
  }
  
  /**
   * Deletes file or directory and all contents of the directory denoted by the File object.
   * @param file File or directory to delete
   */
  public static void erase(File file) {
    
    if (file.isFile()) { // If File(input param) is file, delete it
      file.delete();
    }
    
    else {
      
      // Remove contents of the directory
      File[] directoryContents = file.listFiles();
      
      for (File directoryItem : directoryContents) {
        
        erase(directoryItem); // Recursive call to this function
      }
      
      // The directory is now empty - required when calling File.delete on directory
      file.delete();
    }
  }
  
  /**
   * Prepares temporary directory to unpack downloaded files
   * @param tempDirectoryName name of the root temp directory name for this generator
   * @return absolute path to the temp directory
   */
  public static String prepareTempDirectory(String tempDirectoryName) {
    String temporaryDirectory = System.getProperty("java.io.tmpdir");
    temporaryDirectory = temporaryDirectory + File.separator + tempDirectoryName + File.separator + System.currentTimeMillis();
    
    File temporaryDirectoryFile = new File(temporaryDirectory);
    temporaryDirectoryFile.mkdirs();
    
    return temporaryDirectory;
  }
}
