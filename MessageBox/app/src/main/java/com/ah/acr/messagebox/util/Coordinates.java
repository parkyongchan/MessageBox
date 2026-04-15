package com.ah.acr.messagebox.util;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.coords.MGRSCoord;
import gov.nasa.worldwind.geom.coords.UTMCoord;

public class Coordinates {

	public static String bytesToHex(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : bytes) {
			hexString.append(String.format("%02X", b)); // 대문자 HEX
		}
		return hexString.toString();
	}
	
	public static String mgrsFromLatLon(double lat, double lon){
		
		Angle latitude = Angle.fromDegrees(lat);
		Angle longitude = Angle.fromDegrees(lon);
		return MGRSCoord
				.fromLatLon(latitude, longitude)
				.toString();
	}
	
	public static double[] latLonFromMgrs(String mgrs){
		
		MGRSCoord coord = MGRSCoord.fromString(mgrs);
		return new double[]{ 
			coord.getLatitude().degrees, 
			coord.getLongitude().degrees 
		};
	}
	
	
	public static String utmFromLatLon(double lat, double lon){
		Angle latitude = Angle.fromDegrees(lat);
		Angle longitude = Angle.fromDegrees(lon);
		
		return UTMCoord
				.fromLatLon(latitude, longitude)
				.toString();
	}

	public static double[] latLonFromUTM(int zone, String hemisphere, double easting, double northing) {
		
		UTMCoord coord = UTMCoord.fromUTM(zone, hemisphere.equals("N") ? AVKey.NORTH : AVKey.SOUTH, easting, northing);
		
		return new double[]{ 
				coord.getLatitude().degrees, 
				coord.getLongitude().degrees 
			};
	}

}