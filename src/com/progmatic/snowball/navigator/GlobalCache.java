package com.progmatic.snowball.navigator;

import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GlobalCache {
	
	private static final ReentrantReadWriteLock _globalSkiAreaListLock = new ReentrantReadWriteLock();
	private static TreeMap<Long, SkiArea> _globalSkiAreaList = new TreeMap<>();

	public static SkiArea getSkiAreaById(Long id)
	{
		SkiArea result = null;
		
		_globalSkiAreaListLock.readLock().lock();
		try 
		{
			result = _globalSkiAreaList.get(id);
		} 
		finally 
		{
			_globalSkiAreaListLock.readLock().unlock();
        }
		return result;
	}

	public static void updateSkiArea(Long id, SkiArea sa)
	{
		_globalSkiAreaListLock.writeLock().lock();
		try 
		{
			try
			{
				_globalSkiAreaList.remove(id);
			}
			catch (Exception e){}
			
			_globalSkiAreaList.put(id, sa);
		} 
		finally 
		{
			_globalSkiAreaListLock.writeLock().unlock();
        }
	}
}
