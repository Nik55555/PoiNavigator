package com.progmatic.snowball.utils;

import com.progmatic.snowball.utils.dem_generator.DemGeneratorException;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

/**
 * This class is the initial implementation of the slicing ASTER GDEM TIFF's
 * @author Max Romanov
 * @author Leon Dobnik
 */
public class SplitHgt
{
  private static final int E6 = 1000000;
  private static int splitParts = 10;
  private static int fractionDigits = 2;
  private static int latSizeE6 = E6 / splitParts;
  private static int lonSizeE6 = E6 / splitParts;

  public static String getHgtFilename(int latE6, int lonE6) {
      return ElevationData.getLatNamePart(latE6,latSizeE6,2) +
            ElevationData.getLonNamePart(lonE6,lonSizeE6,2);
  }

  private static class HgtData extends ElevationData
  {
    public HgtData()
    {
    }

    public HgtData(int latE6, int lonE6)
    {
      super(latE6, lonE6);
    }

    public boolean parseName(String fname)
    {
      if (fname.startsWith("ASTGTM2_")) {
        fname = fname.substring(8);
        System.out.println("modified name: " + fname);
      }
      if (fname.endsWith(".bil")) {
        westE6 = (int)Math.floor(171.5 * 1E6);
        eastE6 = (int)Math.floor(172.0 * 1E6);
        northE6 = (int)Math.floor(-43.0 * 1E6);
        southE6 = (int)Math.floor(-43.5 * 1E6);
        return true;
      }
      int idxLatPrefix = fname.indexOf("N");
      if (idxLatPrefix == -1) {
        idxLatPrefix = fname.indexOf("S");
        if (idxLatPrefix == -1) return false;
      }
      int i = idxLatPrefix + 1;
      int npoints = 0;
      while (i < fname.length()) {
        char c = fname.charAt(i);
        if (c < '0' || c > '9') {
          if (npoints > 0 || c != '.')
            break;
          ++npoints;
        }
        ++i;
      }
      double dLat = Double.parseDouble(fname.substring(idxLatPrefix + 1, i));

      int idxLonPrefix = fname.indexOf("E");
      if (idxLonPrefix == -1) {
        idxLonPrefix = fname.indexOf("W");
        if (idxLonPrefix == -1) return false;
      }
      i = idxLonPrefix + 1;
      npoints = 0;
      while (i < fname.length()) {
        char c = fname.charAt(i);
        if (c < '0' || c > '9') {
          if (npoints > 0 || c != '.')
            break;
          ++npoints;
        }
        ++i;
      }
      double dLon = Double.parseDouble(fname.substring(idxLonPrefix + 1, i));

      southE6 = (int)Math.floor(dLat * 1E6);
      if (fname.charAt(idxLatPrefix) == 'S') {
        southE6 = -southE6;
      }
      westE6 = (int)Math.floor(dLon * 1E6);
      if (fname.charAt(idxLonPrefix) == 'W') {
        westE6 = -westE6;
      }
      northE6 = southE6 + E6;
      eastE6 = westE6 + E6;

      System.out.println("parseName(" + fname + "): " + southE6 + "," + westE6);
      return true;
    }

    public void writeJSON(OutputStream out) throws IOException
    {
      out.write("var hgt = [\n".getBytes());
      for(int row = rows - 1; row >= 0; --row) {
        StringBuilder sb = new StringBuilder(columns * 6 + 4);
        sb.append("[ ");
        for(int column = 0; column < columns; ++column) {
          int h = get(row, column);
          sb.append(h).append(",");
        }
        sb.append(" ],\n");
        out.write(sb.toString().getBytes());
      }
      out.write("];\n".getBytes());
    }
  }
  
  /**
   * Extracts DEM data from the TIFF inside the downloaded archive and slices one degree
   * tile into smaller 0.1x0.1 degree tiles.
   * Note: this method has been implemented by following DEMO main method implemented by Max Romanov.
   * The code stayed the same I have only added try with resources (to make sure that resources are released
   * after writing to files) and directory support where files are generated.
   * @param absoluteFilePath location of the downloaded DEM file
   * @throws DemGeneratorException whenever something goes wrong
   */
  public static void extractAndSliceAsterGdem(String absoluteFilePath) throws DemGeneratorException {
    
    try {

      // Step 1: extract coordinate from the downloaded archive
      File downloadedDem = new File(absoluteFilePath);
      String downloadedDemFileName = downloadedDem.getName();
      
      HgtData hgt = new HgtData();
      if (!hgt.parseName(downloadedDemFileName)) {
        throw new DemGeneratorException("Failed to get coordinates from file name: " + downloadedDemFileName);
      }
      
      String currentLatitudeDirectory = downloadedDemFileName.substring(8, 11);
      String currentLongitudeDirectory = downloadedDemFileName.substring(11, 15);

      // Step 2: read elevation data from the TIFF inside archive (produce in memmory HGT file for 1 degree tile)
      try(ZipInputStream zis = new ZipInputStream(new FileInputStream(downloadedDem))) {
        
        ZipEntry entry = null;
        
        while((entry = zis.getNextEntry()) != null ) {

          if (entry.getName().endsWith("_dem.tif")) {
            TIFFStream.readTIFF(hgt, zis, (int)entry.getSize());
            break;
          }
        }
      }
      
      // Step 3: Slice one degree HGT into 0.1 x 0.1 degree pieces
      for(int lat = hgt.southE6; lat < hgt.northE6; lat += latSizeE6) {
        
        for(int lon = hgt.westE6; lon < hgt.eastE6; lon += lonSizeE6) {

          HgtData newHgt = new HgtData(lat, lon);
          newHgt.inflate(latSizeE6, lonSizeE6);
          newHgt.copyFrom(hgt);

          String hgtSliceFileName = getHgtFilename(lat, lon) + ".hgt.gz";
          String hgtSliceDirectory = downloadedDem.getParent() + File.separator + currentLatitudeDirectory + File.separator + 
                                     currentLongitudeDirectory;

          File hgtSliceDirectoryFile = new File(hgtSliceDirectory);
          hgtSliceDirectoryFile.mkdirs();
          
          String hgtSliceAbsolutePath = hgtSliceDirectory + File.separator + hgtSliceFileName;
          
          try(GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(hgtSliceAbsolutePath))) {
            newHgt.writeTo(gzos);
            gzos.finish();
          }
        }
      }
    }
    
    catch(Exception e) { // Bad practice to catch Exception, but it requires fix to the underlaying classes
      throw new DemGeneratorException("Failed to extract and slice 1 degree tile: " + absoluteFilePath, e);
    }
  }

  public static void main (String args []) {
    try {
      String fname = args[0];
      System.out.println("Input file: " + fname);
      HgtData hgt = new HgtData();
      if (!hgt.parseName(fname)) {
        System.err.println("Failed to get coordinates from file name: " + fname);
      }

      System.out.println("Input coordinates: " + hgt.southE6 + "," + hgt.westE6);
      File f = new File(fname);

      FileInputStream fis = new FileInputStream(f);

      if (fname.startsWith("ASTGTM2_") && fname.endsWith(".zip")) {
        ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry entry;
        while( (entry = zis.getNextEntry()) != null ) {
          System.out.println("Zip entry: '" + entry.getName() + "'");
          if (entry.getName().endsWith("_dem.tif")) {
            TIFFStream.readTIFF(hgt, zis, (int)entry.getSize());
            break;
          }
        }
        zis.close();
      } else
      if (fname.endsWith(".hgt.zip")) {
        ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry entry;
        while( (entry = zis.getNextEntry()) != null ) {
          System.out.println("Zip entry: '" + entry.getName() + "'");
          if (entry.getName().endsWith(".hgt")) {
            hgt.setSize((int)entry.getSize());
            System.out.println("Hgt: " + hgt);
            hgt.readFrom(zis);
            break;
          }
        }
        zis.close();
      } else
      if (fname.endsWith(".tif") || fname.endsWith(".tiff")) {
        TIFFStream.readTIFF(hgt, fis, (int)f.length());
      } else
      if (fname.endsWith(".bil")) {
        hgt.setSize((int)f.length() / 2);
        ByteBuffer bb = ByteBuffer.allocate((int)f.length());
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int res;
        int pos = 0;
        byte[] data = bb.array();
        while( (res = fis.read(data, pos, data.length - pos)) > 0 ) {
          pos += res;
        }
        res = 0;
        for (pos = 0; pos < data.length; pos += 4) {
          hgt.set(res, (int)Math.round(bb.getFloat(pos)));
          res += 2;
        }
      } else {
        DataInputStream in = new DataInputStream(fis);
        hgt.setSize((int)f.length());
        hgt.readFrom(in);
        in.close();
      }

      fis.close();

      System.out.println("-43.153728, 171.658748 : " + hgt.interpolate(-43153728, 171658748));
      System.out.println("-43.100000, 171.600000 : " + hgt.interpolate(-43100000, 171600000));

      String n = "orig";
      String outName = "out/" + n + ".hgt";
      System.out.println("Out file: " + outName);
      OutputStream fos = new FileOutputStream(outName);
      hgt.writeTo(fos);
      fos.close();

      outName = "out/" + n + ".tif";
      System.out.println("Out file: " + outName);
      fos = new FileOutputStream(outName);
      TIFFStream.writeTIFF(hgt, fos);
      fos.close();

      outName = "out/" + n + ".json";
      System.out.println("Out file: " + outName);
      fos = new FileOutputStream(outName);
      hgt.writeJSON(fos);
      fos.close();

      for(int lat = hgt.southE6; lat < hgt.northE6; lat += latSizeE6) {
        for(int lon = hgt.westE6; lon < hgt.eastE6; lon += lonSizeE6) {

          HgtData newHgt = new HgtData(lat, lon);
          newHgt.inflate(latSizeE6, lonSizeE6);
          newHgt.copyFrom(hgt);

          outName = "out/" + getHgtFilename(lat,lon) + ".hgt";
          System.out.println("Out file: " + outName);
          fos = new FileOutputStream(outName);
          newHgt.writeTo(fos);
          fos.close();

          outName = "out/" + getHgtFilename(lat,lon) + ".hgt.gz";
          System.out.println("Out file: " + outName);
          fos = new GZIPOutputStream(new FileOutputStream(outName));
          newHgt.writeTo(fos);
          fos.close();

          outName = "out/" + getHgtFilename(lat,lon) + ".tif";
          System.out.println("Out file: " + outName);
          fos = new FileOutputStream(outName);
          TIFFStream.writeTIFF(newHgt, fos);
          fos.close();

          outName = "out/" + getHgtFilename(lat,lon) + ".json";
          System.out.println("Out file: " + outName);
          fos = new FileOutputStream(outName);
          newHgt.writeJSON(fos);
          fos.close();
        }
      }

    }
    catch(Exception e)
    {
      System.err.println("Exception caugth: " + e);
    }

    System. out. println ("Hello World: " + args[0]);

  }
}
