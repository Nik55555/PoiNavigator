package com.progmatic.snowball.utils;

import com.progmatic.snowball.utils.dem_generator.DemGeneratorException;
import com.progmatic.snowball.utils.dem_generator.DemGeneratorUtils;
import com.progmatic.snowball.utils.dem_generator.TileSliceHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class generates ZIP archive of 0.1 x 0.1 degree hgt.gz files collected from the hgt.gz repository. Some
 * pieces my be also collected from the ASTER GDEM server if they are not present on the server. 
 * Note: this class is not thread safe!
 * @author Leon Dobnik
 */
public class DemGenerator {
  
  /**
   * URL of the Japanese download server - the IP of the server is not bound to the hostName so there is no
   * way to hard-code host instead of the IP
   */
  private static final String JAPAN_DEM_SERVER_URL = "http://113.35.103.196/gdServlet/Download";
  
  /**
   * Temporary directory work with files
   */
  private static String TEMP_DIR = null;
  
  /**
   * Template of the ASGTM  GDEM file
   */
  private static final String TIFF_FILE_NAME_TEMPLATE = "ASTGTM2_%s%02d%s%03d.zip";
  
  /**
   * CharSet used for HTTP communication
   */
  private static final String HTTP_CHARSET = "UTF-8";
  
  /**
   * Repository where the special HGT files are located
   */
  private static final String HGT_REPOSITORY_DIR = "/home/dem_repository";
  
  /**
   * Size of the slices
   */
  private static final double SLICE_SIZE = 0.1;
  
  /**
   * Template for formatting HGT file names
   */
  private static final String HGT_FILE_NAME_TEMPLATE = "%s%05.2f%s%06.2f.hgt.gz";
  
  /**
   * Template name for the output ZIP archive
   */
  private static final String ZIP_FILENAME_TEMPLATE = "DemModel_%s.zip";
  
  /**
   * Date pattern for the output ZIP archive
   */
  private static final String ZIP_FILENAME_DATE_PATTERN = "yyyy-MM-dd_HH-mm-ss";
  
  /**
   * The target output directory of the generated ZIP archive
   */
  private static final String ZIP_OUTPUT_DIR = "/home/sb/develop/jboss-aep-6.3/standalone/deployments/download.war";
  
  /**
   * Temporary root directory inside OS temp directory for extracting files
   */
  private static final String TEMP_DIR_DEM_NAME = "MOBANGLES_DEM_TEMP";
  
  /**
   * Minimal size of the downloaded aster GDEM ZIP archive to threat file as successful download
   */
  public static final long ZIP_ASTER_GDEM_MIN_SIZE = 3072;
  
  /**
   * Ensures that double number can be properly compared in for loops - in general it is not safe
   * not compare decimal numbers in Java
   */
  private static final double DELTA_FOR_COMPARSION = 0.0001;
  
  /**
   * Generates DEM archive of the 0.1 x 0.1 degree tiles by following these steps:
   * --> prepare temporary directory for file downloads and zip assembly
   * --> collect pieces of the specified coordinates (if slice is missing get it from 30m Japanese model)
   * --> construct ZIP archive from the collected pieces and place it to the required destination
   * --> remove the temporary directory
   * @param latitude_max latitude of the upper left corner
   * @param longitude_min longitude of the upper left corner
   * @param latitude_min latitude of the  bottom right corner
   * @param longitude_max longitude of the bottom right corner
   * @throws DemGeneratorException whenever something goes wrong in the generation process
   */
  public static void generateDem(int latitude_max, int longitude_min, int latitude_min, int longitude_max) throws DemGeneratorException {
    
    try {
      // Step 1: Prepare temporary directory to download, zip, slice files
      TEMP_DIR = DemGeneratorUtils.prepareTempDirectory(TEMP_DIR_DEM_NAME);

      // Step 2: Collect pieces for each tile and/or download files when pieces are missing
      List<TileSliceHolder> tileSliceHolders = new ArrayList<>();

      for(int i = latitude_min; i < latitude_max; i++) {

        for (int j = longitude_min; j < longitude_max; j++) {

          TileSliceHolder currentTileSliceHolder = collectSlicesOfTile(i, j);

          // Add dto object to the list only when tiles exist
          if (currentTileSliceHolder.getSlicePaths() != null && currentTileSliceHolder.getSlicePaths().size() > 0) {
            tileSliceHolders.add(currentTileSliceHolder);
          }
        }
      }
    
      // Step 3: create ZIP archive from the collected slices
      createZipArchive(tileSliceHolders);
    }
    
    catch(DemGeneratorException e) { // Just rethrow we are cathing here, to make sure the cleanup wil occur (step4 in finally)
      throw e;
    }
    
    finally {
      // Step 4: remove the temporary directory
      File temporaryDirectory = new File(TEMP_DIR);
      DemGeneratorUtils.erase(temporaryDirectory);
    }        
  }
  
  /**
   * Collects all slices (0.1 x 0.1 degree pieces) for the specified latitude and longitude by following this algorithm:
   * For every 0.1 x 0.1 degree slice do:
   * --> check if the file exists in the repository
   * ----> if so, add it to the list
   * --> otherwise
   * ----> download the missing 1 degree tile (from which the slice is missing), slice it and then try to add it
   * Also note:
   * --> the tile should be downloaded only once
   * --> aster GDEM model does not offer 1 degree tiles where is sea (entire tile) - so if the tile does not exist, it will not be processed 
   * @param latitude of the 1 degree tile to collect pieces (bottom left corner)
   * @param longitude of the 1 degree tile to collet pieces (bottom left corner)
   * @throws DemGeneratorException if generating DEM fails for some reason
   * @return TileSliceHolder object
   */
  private static TileSliceHolder collectSlicesOfTile(int latitude, int longitude) throws DemGeneratorException {
    
    List<String> tilePaths = new ArrayList<>();
    
    String latitudeDirectory = DemGeneratorUtils.produceLatitudeDirectoryName(latitude);
    String longitudeDirectory = DemGeneratorUtils.produceLongitudeDirectoryName(longitude);
    
    double currentLatitude = latitude;
    double latitudeEnd = latitude + 1 - DELTA_FOR_COMPARSION;
    
    double currentLongitude = longitude;
    double longitudeEnd = longitude + 1 - DELTA_FOR_COMPARSION;
    
    Boolean tileProcessed = Boolean.FALSE; // Has been this tile already proessed -> set flag to avoid multiple downloads of the same tile
    
    while(currentLatitude < latitudeEnd) {
      
      while(currentLongitude <longitudeEnd) {
        
        String sliceRelativePath = latitudeDirectory + File.separator + longitudeDirectory + File.separator + 
                                   String.format(HGT_FILE_NAME_TEMPLATE, 
                                           DemGeneratorUtils.latitudeToNorthSouth(currentLatitude), Math.abs(currentLatitude), 
                                           DemGeneratorUtils.longitudeToWestEast(currentLongitude), Math.abs(currentLongitude));
        
        String currentSliceHgtRepositoryAbsolutePath = HGT_REPOSITORY_DIR + File.separator + sliceRelativePath;
        File currentSliceHgtRepositoryFile = new File(currentSliceHgtRepositoryAbsolutePath);                           
        
        if (currentSliceHgtRepositoryFile.exists()) {
          tilePaths.add(currentSliceHgtRepositoryAbsolutePath);
        }
        
        else {
          // Download and slice missing tile (if it has not been already done)
          if (tileProcessed != null && !tileProcessed) {
            tileProcessed = processMissingTile(latitude, longitude);
          }
          
          if (tileProcessed != null) { // If the tile was process
            
             // Check if the missing slice now exists
            String currentSliceTempAbsolutePath = TEMP_DIR + File.separator + sliceRelativePath;
            File currentSliceTempFile = new File(currentSliceTempAbsolutePath);

            if (currentSliceTempFile.exists()) {
              tilePaths.add(currentSliceTempAbsolutePath);
            }
            
            else {
              throw new DemGeneratorException("The piece " + currentSliceTempAbsolutePath + " does not exist, although it should be downloaded!");
            }
          }
        }
        
        currentLongitude = currentLongitude + SLICE_SIZE;
      }
      
      currentLongitude = longitude;
      currentLatitude = currentLatitude + SLICE_SIZE;
    }

    return new TileSliceHolder(latitude, longitude, tilePaths);
  }
  
  /**
   * Downloads the missing 1 degree tile from the Japan server, slices the tile into 
   * small pieces. The function return true if the processing was successful and NULL
   * to indicate that file has not been even processed - the 1 degree is not available.
   * The option of unsuccessful processing is not supported at the moment
   * @param latitude of the tile to process
   * @param longitude of the tile to process
   * @return true -> processing successful, null -> not processed, false -> unsupported
   * @throws DemGeneratorException
   */
  private static Boolean processMissingTile(int latitude, int longitude) throws DemGeneratorException {
    
    Boolean processingStatus;
    
    // 1) Download the missing file
    String downloadedTile = downloadDemFile(latitude, longitude);
    
    // 2) Extract & slice the missing file -> only when ZIP is successfully downloaded
    if (downloadedTile != null) {
      SplitHgt.extractAndSliceAsterGdem(downloadedTile);
      processingStatus = Boolean.TRUE;
    }
    
    else {
      processingStatus = null;
    }
    
    return processingStatus;
  }
  
  /**
   * Constructs ZIP archive for files (0.1 degree hgt.gz files) located in input parameters
   * Note: ZIP file is generated in TEMP directory and then moved/copied to the target destination
   * @param tileSliceHolders list of TileSliceHolder objects, each holds files for 1 degree tiles
   * @throws DemGeneratorException whenever creating ZIP archive fails for some reason
   */
  private static void createZipArchive(List<TileSliceHolder> tileSliceHolders) throws DemGeneratorException {
    
    if (tileSliceHolders.size() < 1) {
      throw new DemGeneratorException("There are no files to include into the DEM archive! Have you provided the correct coordinates? " +
                                      "For example: the specified coordinates are entirely on the sea.");
    }
    
    SimpleDateFormat sdf = new SimpleDateFormat(ZIP_FILENAME_DATE_PATTERN);
    String zipFileNameDatePart = sdf.format(new Date(System.currentTimeMillis()));
    String zipFileName = String.format(ZIP_FILENAME_TEMPLATE, zipFileNameDatePart);
    String outputZipAbsolutePath = TEMP_DIR + File.separator + String.format(ZIP_FILENAME_TEMPLATE, zipFileName);
    
    HashSet<String> generatedDirectories = new HashSet<>();
    
    try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZipAbsolutePath))) {
      
      zos.setLevel(Deflater.BEST_COMPRESSION);
    
      for (TileSliceHolder tileSliceHolder : tileSliceHolders) { // Each TileSliceHolder contains tile for 1 degree tile

        /**
         * Take path of the first slice and remove path of the HGT repository / temp directory. This will produce
         * following relative path latitude/longitudeDir/fileName.hgt.gz for example N47/E009/N47.00E009.00.hgt.gz.
         * From this relative path it is easily to extract longitude and latitude directory
         */
        String firstSlicePath = tileSliceHolder.getSlicePaths().get(0);
        String relativePath = produceRelativeSlicePath(firstSlicePath);
        File relativePathFile = new File(relativePath);
        String longitudeDirectory = relativePathFile.getParentFile().getAbsolutePath() + File.separator;
        String latitudeDirectory = File.separator + relativePathFile.getParentFile().getParentFile().getName() + File.separator;
        
        if (!generatedDirectories.contains(latitudeDirectory)) {
          zos.putNextEntry(new ZipEntry(latitudeDirectory));
          generatedDirectories.add(latitudeDirectory);
          zos.flush();
        }
        
        if (!generatedDirectories.contains(longitudeDirectory)) {
          zos.putNextEntry(new ZipEntry(longitudeDirectory));
          generatedDirectories.add(longitudeDirectory);
          zos.flush();
        }
        
        for (String absoluteFilePath : tileSliceHolder.getSlicePaths()) {
          
          String relativeFilePath = produceRelativeSlicePath(absoluteFilePath);
          ZipEntry zipEntry = new ZipEntry(relativeFilePath);
          zos.putNextEntry(zipEntry);
          
          try(FileInputStream fis = new FileInputStream(absoluteFilePath)) {
            
            int read;
            byte[] buffer = new byte[8192];
            
            while((read = fis.read(buffer)) > -1) {
              zos.write(buffer, 0, read);
            }
          }
          
          // Free resources
          zos.flush();
          zos.closeEntry();
        }
      }
      
      // Finish ZIP stream
      zos.finish();
      zos.close();
      
      // Move constructed ZIP file to the final output directory
      String finalOutputAbsolutePath = ZIP_OUTPUT_DIR + File.separator + zipFileName;
      File generatedZipArchive = new File(outputZipAbsolutePath); // Constructed ZIP archive in TEMP directory
      File finalGeneratedZipArchive = new File(finalOutputAbsolutePath); // Target location of the costructed ZIP archive
      generatedZipArchive.renameTo(finalGeneratedZipArchive); // Move the archive
    }
    
    catch(IOException e) {
      throw new DemGeneratorException("Failed to generate ZIP arhive of the DEM model", e);
    }
  }
  
  /**
   * Produces relative path of the hgt.gz file inside zip archive. The path is:
   * latitudeDir/longitudeDir/fileName.hgt.gz. For example: N48/E009/N48.10E009.20.hgt.gz
   * @param absoluteSlicePath absolute path to the slice
   * @return relative path to the slice
   */
  private static String produceRelativeSlicePath(String absoluteSlicePath) {
    
    String relativePath = absoluteSlicePath.replace(HGT_REPOSITORY_DIR, ""); // Remove path of the repository
    relativePath = relativePath.replace(TEMP_DIR, ""); // Remote path of temp directory
    
    return relativePath;
  }
 
  /**
   * Downloads 1 degree TILE from the Japanese server (ASTER GDEM). When 1 degree tile does not exist,
   * a null is returned
   * @param latitude of the missing tile
   * @param longitude of the missing tile
   * @return absolute path (including file name) of the downloaded file, return null when file does not exist
   * @throws DemGeneratorException when the downloading of the file fails for some reason
   */
  private static String downloadDemFile(int latitude, int longitude) throws DemGeneratorException {
    
    try {
      
      // Post parameter with file name (insert proper easting and nortning)
      String fileName = String.format(TIFF_FILE_NAME_TEMPLATE, DemGeneratorUtils.latitudeToNorthSouth(latitude), Math.abs(latitude), 
                                                               DemGeneratorUtils.longitudeToWestEast(longitude), Math.abs(longitude));
      String postParameter = "_gd_download_file_name=" + fileName;
      String absoluteDownloadPath = TEMP_DIR + File.separator + fileName;
      
      // Prepare connection with proper parameters and connect
      URL url = new URL(JAPAN_DEM_SERVER_URL);
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("charset", HTTP_CHARSET);
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
      connection.setRequestProperty("Content-Length", Integer.toString(postParameter.length()));
      connection.connect();
      
      // Write post parameter
      try(OutputStream os = connection.getOutputStream()) {
        os.write(postParameter.getBytes(Charset.forName(HTTP_CHARSET)));
        os.flush();
      }
      
      // Now let's download file
      if (connection.getResponseCode() == 200) {
        
        try(InputStream is = connection.getInputStream();
            FileOutputStream fos = new FileOutputStream(absoluteDownloadPath)) {
          
            // Read the file in chunks and write it to the disk
            int read;
            byte[] buffer = new byte[8192];
          
            while((read = is.read(buffer)) > 0) {
              fos.write(buffer, 0, read);
            }
          }
        }
      
        /**
         * The Japanese server returns 200 OK (HTTP HEADER), but in the text file it says that an error has 
         * occurred. Since due DEM file are large, we can check by the file size if file has been really downloaded.
         * The aster GDEM does not offer files which for certain areas - for example when full tile is on the sea
         */
        File absoluteDownloadPathFile = new File(absoluteDownloadPath);
        return (absoluteDownloadPathFile.length() > ZIP_ASTER_GDEM_MIN_SIZE) ? absoluteDownloadPath : null;
      }
    
      catch(IOException e) {
        throw new DemGeneratorException("Failed to download for latitude: " + latitude + " and logitude: " + longitude, e);
    }
  }
}