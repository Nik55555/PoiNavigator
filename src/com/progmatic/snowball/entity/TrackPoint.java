package com.progmatic.snowball.entity;

// Generated 01.06.2015 11:10:38 by Hibernate Tools 3.4.0.CR1

import java.util.Date;

/**
 * TrackPoint generated by hbm2java
 */
//@Entity
//@Table(name = "track_point", schema = "public")
//@SequenceGenerator(name="track_point_id_seq", sequenceName="track_point_id_seq", allocationSize=1)
//@Restrict
public class TrackPoint implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long id;
	private Double latitude;
	private Double longitude;
	private Integer elevation;
	private Date time;
	private Track track;

	public TrackPoint() {
	}

	public TrackPoint(long id) {
		this.id = id;
	}

	public TrackPoint(long id, Double latitude,
			Double longitude, Integer elevation, Date time, Track track) {
		this.id = id;
		this.latitude = latitude;
		this.longitude = longitude;
		this.elevation = elevation;
		this.time = time;
		this.track = track;
	}

//	@Id
//	@Column(name = "id", unique = true, nullable = false)
//  	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="track_point_id_seq")
	public long getId() {
		return this.id;
	}

	public void setId(long id) {
		this.id = id;
	}

	//@Column(name = "latitude", precision = 17, scale = 17)
	public Double getLatitude() {
		return this.latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	//@Column(name = "longitude", precision = 17, scale = 17)
	public Double getLongitude() {
		return this.longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	//@Column(name = "elevation")
	public Integer getElevation() {
		return this.elevation;
	}

	public void setElevation(Integer elevation) {
		this.elevation = elevation;
	}

	//@Temporal(TemporalType.TIMESTAMP)
	//@Column(name = "time", length = 29)
	public Date getTime() {
		return this.time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	//@ManyToOne(fetch = FetchType.LAZY)
	//@JoinColumn(name = "track_id")
	public Track getTrack() {
		return this.track;
	}

	public void setTrack(Track track) {
		this.track = track;
	}

	
}
