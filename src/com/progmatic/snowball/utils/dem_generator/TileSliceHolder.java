package com.progmatic.snowball.utils.dem_generator;

import java.util.List;

/**
 * This class is DTO for information related to 1 degree tile of DEM model. It contains
 * its latitude, longitude and absolute paths to 1 degree tile it represents.
 * @author Leon Dobnik
 */
public class TileSliceHolder {
  
  /**
   * Latitude of the 1 degree tile 
   */
  private final int latitude;
  
  /**
   * Longitude of the 1 degree tile
   */
  private final int longitude;
  
  /**
   * Slices of 1 degree tile (absolute paths to the file)
   */
  private final List<String> slicePaths;
  
  /**
   * Create new instance of TileSliceHolder with specified parameters
   * @param latitude latitude of the 1 degree tile
   * @param longitude longitude of the 1 degree tile
   * @param slicePaths absolute path to all slices of this 1 degree tiles
   */
  public TileSliceHolder(final int latitude, final int longitude, final List<String> slicePaths) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.slicePaths = slicePaths;
  }
  
  /**
   * Returns longitude of this 1 degree tile
   * @return longitude of this 1 degree tile
   */
  public int getLongitude() {
    return longitude;
  }
  
  /**
   * Returns latitude of this 1 degree tile
   * @return latitude of this 1 degree tile
   */
  public int getLatitude() {
    return latitude;
  }
  
  /**
   * Absolute paths of 1 degree tile slices represented by this 1 degree tile
   * @return 
   */
  public List<String> getSlicePaths() {
    return slicePaths;
  }
}