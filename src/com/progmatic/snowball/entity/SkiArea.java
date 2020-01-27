package com.progmatic.snowball.entity;

// Generated 23.06.2015 16:19:08 by Hibernate Tools 3.4.0.CR1

import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/**
 * SkiArea generated by hbm2java
 */
//@Entity
//@Table(name = "ski_area", schema = "public")
//@SequenceGenerator(name="ski_area_id_seq", sequenceName="ski_area_id_seq", allocationSize=1)
//@Restrict
//@XmlRootElement
//@XmlAccessorType(XmlAccessType.NONE)
public class SkiArea implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private long id;
	private Long domainId;
	private String name;
	private String description;
	private Set<LayerData> layerDatas = new HashSet<LayerData>(0);
	private Set<NodalPoint> nodalPoints = new HashSet<NodalPoint>(0);
	private Set<SkiArea> skiAreas = new HashSet<SkiArea>(0);
	private SkiArea domain;
	private double x1;
	private double y1;
	private double x2;
	private double y2;
    private Set<User> users = new HashSet<User>(0);
    private Set<LayerSvg> layerSvgs = new HashSet<LayerSvg>(0);
	private Date updated;
	private int schemeStatus;
    //private Set<WeatherInfo> weatherInfos = new HashSet<WeatherInfo>(0);
	

	public SkiArea() {
	}

	public SkiArea(long id, String name) {
		this.id = id;
		this.name = name;
	}

	public SkiArea(long id, Long domainId, String name, String description,
			Set<LayerData> layerDatas, Set<NodalPoint> nodalPoints, Set<SkiArea> skiAreas, SkiArea domain,
			double x1, double y1, double x2, double y2, Set<User> users, Set<LayerSvg> layerSvgs, Date updated,
			int schemeStatus) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.layerDatas = layerDatas;
		this.nodalPoints = nodalPoints;
		this.domainId = domainId;
		this.domain = domain;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.users = users;
		this.layerSvgs = layerSvgs;
		this.updated = updated;
		this.schemeStatus = schemeStatus;
	}

	//@Id
	//@Column(name = "id", unique = true, nullable = false)
  	//@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="ski_area_id_seq")
	//@XmlElement(name="id")
	public long getId() {
		return this.id;
	}

	public void setId(long id) {
		this.id = id;
	}

	//@Column(name = "name", unique = true, nullable = false, length = 125)
	//@NotNull
	//@Size(max = 125)
	//@XmlElement(name="name")
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	//@Column(name = "description", length = 1024)
	//@Size(max = 1024)
	//@XmlElement(name="description")
	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	//@OneToMany(fetch = FetchType.LAZY, mappedBy = "skiArea")
	public Set<LayerData> getLayerDatas() {
		return this.layerDatas;
	}

	public void setLayerDatas(Set<LayerData> layerDatas) {
		this.layerDatas = layerDatas;
	}

	//@OneToMany(fetch = FetchType.LAZY, mappedBy = "skiArea")
	//@JsonIgnore
	public Set<NodalPoint> getNodalPoints() {
		return this.nodalPoints;
	}

	public void setNodalPoints(Set<NodalPoint> nodalPoints) {
		this.nodalPoints = nodalPoints;
	}

	//@Column(name = "ski_area_id", insertable = false, updatable = false)
	//@XmlElement(name="domainId")
	public Long getDomainId() {
		return this.domainId;
	}

	public void setDomainId(Long domainId) {
		this.domainId = domainId;
	}

	//@ManyToOne(fetch = FetchType.LAZY)
	//@JoinColumn(name = "ski_area_id")
	//@JsonIgnore
	public SkiArea getDomain() {
		return this.domain;
	}
	public void setDomain(SkiArea domain) {
		this.domain = domain;
	}

	//@Column(name = "x1", nullable = false, precision = 17, scale = 17)
	//@XmlElement(name="x1")
	public double getX1() {
		return this.x1;
	}

	public void setX1(double x1) {
		this.x1 = x1;
	}

	//@Column(name = "y1", nullable = false, precision = 17, scale = 17)
	//@XmlElement(name="y1")
	public double getY1() {
		return this.y1;
	}

	public void setY1(double y1) {
		this.y1 = y1;
	}

	//@Column(name = "x2", nullable = false, precision = 17, scale = 17)
	//@XmlElement(name="x2")
	public double getX2() {
		return this.x2;
	}

	public void setX2(double x2) {
		this.x2 = x2;
	}

	//@Column(name = "y2", nullable = false, precision = 17, scale = 17)
	//@XmlElement(name="y2")
	public double getY2() {
		return this.y2;
	}

	public void setY2(double y2) {
		this.y2 = y2;
	}

	//@Column(name = "scheme_status", nullable = false)
	//@NotNull
	//@XmlElement(name="schemeStatus")
	public int getSchemeStatus() {
		return this.schemeStatus;
	}

	public void setSchemeStatus(int schemeStatus) {
		this.schemeStatus = schemeStatus;
	}
	
	public String schemeStatusString()
	{
		try
		{
			return schemeStatuses()[this.getSchemeStatus()];
		}
		catch (Exception e){}
		
		return "Not detected";
	}
	
	public String[] schemeStatuses()
	{
		String[] result = new String[4];
		
		result[0] = "Not availiable";
		result[1] = "Availiable";
		result[2] = "Coming soon";
		result[3] = "Test";
		
		return result;

	}

	public String domainName()
	{
		return (this.getDomain() == null) ? "" : this.getDomain().getName();
	}
	
	public boolean skiAreaIsDomain()
	{
		return (this.getLayerDatas().size() == 0 && this.getDomain() == null && this.getSkiAreas().size() > 0) ? true : false;
	}

	//@OneToMany(fetch = FetchType.LAZY, mappedBy = "domain")
	public Set<SkiArea> getSkiAreas() {
		return this.skiAreas;
	}

	public void setSkiAreas(Set<SkiArea> skiAreas) {
		this.skiAreas = skiAreas;
	}

	//@OneToMany(fetch = FetchType.LAZY, mappedBy = "skiArea")
	//@JsonIgnore
	public Set<LayerSvg> getLayerSvgs() {
		return this.layerSvgs;
	}

	public void setLayerSvgs(Set<LayerSvg> layerSvgs) {
		this.layerSvgs = layerSvgs;
	}

	//@OneToMany(fetch = FetchType.LAZY, mappedBy = "skiArea")
	//@JsonIgnore
	//public Set<WeatherInfo> getWeatherInfos() {
	//	return this.weatherInfos;
	//}

	//public void setWeatherInfos(Set<WeatherInfo> weatherInfos) {
	//	this.weatherInfos = weatherInfos;
	//}

	//@ManyToMany(fetch=FetchType.LAZY)
    //@JoinTable(name="ski_area_user", schema="public", joinColumns = { 
    //    @JoinColumn(name="ski_area_id", nullable=false, updatable=false) }, inverseJoinColumns = { 
    //    @JoinColumn(name="user_id", nullable=false, updatable=false) })
	//@JsonIgnore
    public Set<User> getUsers() {
        return this.users;
    }
    
    public void setUsers(Set<User> users) {
        this.users = users;
    }
    
	//@Temporal(TemporalType.TIMESTAMP)
	//@Column(name = "updated", length = 29)
	//@XmlElement(name="updated")
	public Date getUpdated() {
		return this.updated;
	}

	public void setUpdated(Date updated) {
		this.updated = updated;
	}

}