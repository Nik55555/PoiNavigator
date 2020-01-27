package com.progmatic.snowball.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class ServerProperties {

	//private static String settingsFile = "../standalone/deployments/snowball.war/WEB-INF/classes/server.properties";
	//private static String settingsFile = "/home/sb/develop/jboss-eap-6.3/standalone/deployments/snowball.war/WEB-INF/classes/server.properties";
	private static String settingsFile = Thread.currentThread().getContextClassLoader().getResource("server.properties").getPath();
	
	
	public static Properties GetProperties()
	{
		Properties result = new Properties();
		
		try 
		{
			InputStream is = new FileInputStream(settingsFile);
			result.load(is);
			is.close();
		} 
		catch (Exception e){ e.printStackTrace();}
		
		return result;
	}
	
	public static boolean SetProperty(String key, String value)
	{
		Properties properties = GetProperties();
		OutputStream output = null;
		
		try 
		{
			output = new FileOutputStream(settingsFile);
			properties.setProperty(key, value);
			properties.store(output, null);
		} 
		catch (FileNotFoundException e) 
		{
			System.out.println("Server properties file does not exists!");
			return false;
		}
		catch (IOException e) 
		{
			System.out.println("Can't write a property: " + key);
			return false;
		}
		finally
		{
			try {
				output.close();
			} catch (IOException e) {}
		}
	
		return true;
	}

	public static String GetProperty(String key)
	{
		Properties properties = GetProperties();
		String result = null;

		try 
		{
			result = properties.getProperty(key);
		} 
		catch (Exception e) 
		{
			System.out.println("Can't read a property: " + key);
		}

		return result;
	}
}
