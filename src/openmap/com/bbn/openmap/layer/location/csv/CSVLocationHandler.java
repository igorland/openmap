// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/location/csv/CSVLocationHandler.java,v $
// $RCSfile: CSVLocationHandler.java,v $
// $Revision: 1.5 $
// $Date: 2003/10/23 21:09:31 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.layer.location.csv;


/*  Java  */
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;

/*  OpenMap  */
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.SwingWorker;
import com.bbn.openmap.util.CSVTokenizer;
import com.bbn.openmap.util.quadtree.QuadTree;
import com.bbn.openmap.util.PropUtils;
import com.bbn.openmap.layer.DeclutterMatrix;
import com.bbn.openmap.layer.location.*;

/**  
 * The CSVLocationLayer is a LocationHandler designed to let you put
 * data on the map based on information from a Comma Separated
 * Value(CSV) file.  It's assumed that the each row in the file refers
 * to a certain location, and that location contains a name label, a
 * latitude and a longitude (both in decimal degrees).
 *
 * <P>The individual fields must not have leading whitespace.
 *
 * <P>The CSVLocationLayer gives you some basic functionality.  The
 * properties file lets you set defaults on whether to draw the
 * locations and the names by default.  For crowded layers, having all
 * the names displayed might cause a cluttering problem.  In gesture
 * mode, OpenMap will display the name of each location as the mouse
 * is passed over it.  Pressing the left mouse button over a location
 * brings up a popup menu that lets you show/hide the name label, and
 * also to display the entire row contents of the location CSV file in
 * a Browser window that OpenMap launches.
 *
 * <P>If you want to extend the functionality of this LocationHandler,
 * there are a couple of methods to focus your changes: The
 * setProperties() method lets you add properties to set from the
 * properties file.  The createData() method, by default, is a
 * one-time method that creates the graphic objects based on the CSV
 * data.  By modifying these methods, and creating a different
 * combination graphic other than the CSVLocation, you can create
 * different layer effects pretty easily.
 *
 * <P>The locationFile property should contain a URL referring to the file.
 * This can take the form of file:/myfile.csv for a local file or
 * http://somehost.org/myfile.csv for a remote file.
 *
 * <P>In the openmap.properties file (for instance):<BR>
 * <pre>
 * # In the section for the LocationLayer:
 * locationLayer.locationHandlers=csvlocationhandler
 * 
 * csvlocationhandler.class=com.bbn.openmap.layer.location.csv.CSVLocationHandler
 * csvlocationhandler.locationFile=/data/worldpts/WorldLocs_point.csv
 * csvlocationhandler.csvFileHasHeader=true
 * csvlocationhandler.locationColor=FF0000
 * csvlocationhandler.nameColor=008C54
 * csvlocationhandler.showNames=false
 * csvlocationhandler.showLocations=true
 * csvlocationhandler.nameIndex=0
 * csvlocationhandler.latIndex=8
 * csvlocationhandler.lonIndex=10
 * # Optional property, if the eastern hemisphere longitudes are negative.  False by default.
 * csvlocationhandler.eastIsNeg=false
 * </pre>
 */
public class CSVLocationHandler extends AbstractLocationHandler
    implements LocationHandler, ActionListener {
        	
    /** The path to the primary CSV file holding the locations. */
    protected String locationFile;
    /** The property describing the locations of location data. */
    public static final String LocationFileProperty = "locationFile";
    /** Set if the CSVFile has a header record.  Default is false. */
    public final static String csvHeaderProperty = "csvFileHasHeader";
    /** The storage mechanism for the locations. */
    protected QuadTree quadtree = null;
    
    /** The property describing whether East is a negative value. */
    public static final String eastIsNegProperty = "eastIsNeg";
    /** Are east values really negative with this file? */
    protected boolean eastIsNeg = false;
    /**
     * Flag that specifies that the first line consists of header
     * information, and should not be mapped to a graphic. 
     */
    protected boolean csvHasHeader = false;

    ///////////////////////
    // Name label variables

    /** Index of column in CSV to use as name of location. */
    protected int nameIndex = -1;
    /** Property to use to designate the column of the CSV file to use
     * as a name. */
    public static final String NameIndexProperty = "nameIndex";


    ////////////////////////
    // Location Variables
		
    /** Property to use to designate the column of the CSV file to use
     * as the latitude. */
    public static final String LatIndexProperty = "latIndex";
    /** Property to use to designate the column of the CSV file to use
     * as the longitude. */
    public static final String LonIndexProperty = "lonIndex";
    /** Property to use to designate the column of the CSV file to use
     * as an icon URL */
    public static final String IconIndexProperty = "iconIndex";
    /**
     * Property to set an URL for an icon image to use for all the
     * locations that don't have an image defined in the csv file, or
     * if there isn't an icon defined in the csv file for any of the
     * locations and you want them all to have the same icon.
     */
    public static final String DefaultIconURLProperty = "defaultIconURL";

    /** Index of column in CSV to use as latitude of location. */
    protected int latIndex = -1;
    /** Index of column in CSV to use as logitude of location. */
    protected int lonIndex = -1;
    /** Index of column in CSV to use as URL of the icon. */
    protected int iconIndex = -1;

    protected String defaultIconURL = null;

    /** 
     * The default constructor for the Layer.  All of the attributes
     * are set to their default values.
     */
    public CSVLocationHandler () {}

    /** 
     * The properties and prefix are managed and decoded here, for
     * the standard uses of the CSVLocationHandler.
     *
     * @param prefix string prefix used in the properties file for this layer.
     * @param properties the properties set in the properties file.  
     */
    public void setProperties(String prefix,
			      java.util.Properties properties) {
	super.setProperties(prefix, properties);

	prefix = PropUtils.getScopedPropertyPrefix(prefix);

	locationFile = properties.getProperty(prefix + LocationFileProperty);

	latIndex = PropUtils.intFromProperties(properties, 
						prefix + LatIndexProperty, -1);
	lonIndex = PropUtils.intFromProperties(properties, 
						prefix + LonIndexProperty, -1);
	iconIndex = PropUtils.intFromProperties(properties, 
						prefix + IconIndexProperty, -1);
	nameIndex = PropUtils.intFromProperties(properties, 
						prefix + NameIndexProperty, -1);
	eastIsNeg = PropUtils.booleanFromProperties(properties, 
						     prefix + eastIsNegProperty, false);
	defaultIconURL = properties.getProperty(prefix + DefaultIconURLProperty);

	csvHasHeader = PropUtils.booleanFromProperties(properties, prefix + csvHeaderProperty, false);

	if (Debug.debugging("location")) {
	    Debug.output("CSVLocationHandler indexes:\n  latIndex = " + 
			 latIndex + "\n  lonIndex = " + 
			 lonIndex + "\n  nameIndex = " +
			 nameIndex + "\n  has header = " + csvHasHeader);
	}
    }
    
    /**
     * PropertyConsumer method, to fill in a Properties object,
     * reflecting the current values of the layer.  If the layer has a
     * propertyPrefix set, the property keys should have that prefix
     * plus a separating '.' prepended to each propery key it uses for
     * configuration.
     *
     * @param props a Properties object to load the PropertyConsumer
     * properties into.
     * @return Properties object containing PropertyConsumer property
     * values.  If getList was not null, this should equal getList.
     * Otherwise, it should be the Properties object created by the
     * PropertyConsumer.  
     */
    public Properties getProperties(Properties props) {
	props = super.getProperties(props);

	String prefix = PropUtils.getScopedPropertyPrefix(this);

	props.put(prefix + "class", this.getClass().getName());
	props.put(prefix + LocationFileProperty, PropUtils.unnull(locationFile));

	props.put(prefix + eastIsNegProperty, new Boolean(eastIsNeg).toString());
	props.put(prefix + NameIndexProperty, (nameIndex != -1?Integer.toString(nameIndex):""));
	props.put(prefix + LatIndexProperty, (latIndex != -1?Integer.toString(latIndex):""));
	props.put(prefix + LonIndexProperty, (lonIndex != -1?Integer.toString(lonIndex):""));
	props.put(prefix + IconIndexProperty, (iconIndex != -1?Integer.toString(iconIndex):""));
	props.put(prefix + DefaultIconURLProperty, PropUtils.unnull(defaultIconURL));

	return props;
    }

    /**
     * Method to fill in a Properties object with values reflecting
     * the properties able to be set on this PropertyConsumer.  The
     * key for each property should be the raw property name (without
     * a prefix) with a value that is a String that describes what the
     * property key represents, along with any other information about
     * the property that would be helpful (range, default value,
     * etc.).  This method takes care of the basic LocationHandler
     * parameters, so any LocationHandlers that extend the
     * AbstractLocationHandler should call this method, too, before
     * adding any specific properties.
     *
     * @param list a Properties object to load the PropertyConsumer
     * properties into.  If getList equals null, then a new Properties
     * object should be created.
     * @return Properties object containing PropertyConsumer property
     * values.  If getList was not null, this should equal getList.
     * Otherwise, it should be the Properties object created by the
     * PropertyConsumer.  
     */
    public Properties getPropertyInfo(Properties list) {
	list = super.getPropertyInfo(list);

	list.put("class" + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.NonEditablePropertyEditor");
	list.put(LocationFileProperty, "URL of file containing location information.");
	list .put(LocationFileProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.FUPropertyEditor");
	list.put(eastIsNegProperty, "Flag to note that negative latitude are over the eastern hemisphere.");
	list.put(eastIsNegProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");
	list.put(NameIndexProperty, "The column index, in the location file, of the location label text.");
	list.put(LatIndexProperty, "The column index, in the location file, of the latitudes.");
	list.put(LonIndexProperty, "The column index, in the location file, of the longitudes.");
	list.put(IconIndexProperty, "The column index, in the location file, of the icon for locations (optional).");
	list.put(DefaultIconURLProperty, "The URL of an image file to use as a default for the location markers (optional).");

	return list;
    }

    public void reloadData() {
	quadtree = createData();
    }
    
    /**
     * Look at the CSV file and create the QuadTree holding all the
     * Locations.
     */
    protected QuadTree createData() {
	
	QuadTree qt = new QuadTree(90.0f, -180.0f, -90.0f, 180.0f, 100, 50f);

	if (latIndex == -1 || lonIndex == -1){
	    Debug.error("CSVLocationHandler: createData(): Index properties for Lat/Lon/Name are not set properly! lat index:" + latIndex + ", lon index:" + lonIndex);
	    return null;
	}
	BufferedReader streamReader = null;
	int lineCount = 0;
	Object token = null;

	// readHeader should be set to true if the first line has
	// been read, or if the csvHasHeader is false.
	boolean readHeader = !csvHasHeader;

	try {

	    // This lets the property be specified as a file name
	    // even if it's not specified as file:/<name> in
	    // the properties file.
	    
	    URL csvURL = PropUtils.getResourceOrFileOrURL(null, locationFile); 
	    streamReader = new BufferedReader(new InputStreamReader(csvURL.openStream()));
	    CSVTokenizer csvt = new CSVTokenizer(streamReader);

	    String name = null;
	    float lat = 0;
	    float lon = 0;
	    Location loc = null;
	    String iconURL = null;

	    token = csvt.token();

	    Debug.message("csvlocation", "CSVLocationHandler: Reading File:" 
			  + locationFile
			  + " NameIndex: " + nameIndex
			  + " latIndex: " + latIndex
			  + " lonIndex: " + lonIndex
			  + " iconIndex: " + iconIndex
			  + " eastIsNeg: " + eastIsNeg);

	    while (!csvt.isEOF(token)) {
		int i = 0;

		Debug.message("csvlocation", "CSVLocationHandler| Starting a line | have" + (readHeader?" ":"n't ") + "read header");
		
		while (!csvt.isNewline(token) && !csvt.isEOF(token)){

		    if (readHeader) {
			if (i == nameIndex) {
			    name = (String)token;
			} else if (i == latIndex) {
			    lat = ((Double)token).floatValue();
			} else if (i == lonIndex) {
			    lon = ((Double)token).floatValue();
			    if (eastIsNeg) {
				lon *= -1;
			    }
			} else if (i == iconIndex) {
			    iconURL = (String)token;
			}
		    }

		    token = csvt.token();
		    // For some reason, the check above doesn't always
		    // work
		    if (csvt.isEOF(token)) {
			break;
		    }
		    i++;
		}

		if (!readHeader) {
		    readHeader = true;
		} else {
		    lineCount++;

		    // 		Debug.output(iconURL);
		    if (iconURL == null && defaultIconURL != null) {
			iconURL = defaultIconURL;
		    }

		    loc = createLocation(lat, lon, name, iconURL);

		    qt.put(lat, lon, loc);
		}
		token = csvt.token();
	    }
	} catch (java.io.IOException ioe){
	    throw new com.bbn.openmap.util.HandleError(ioe);
	} catch (ArrayIndexOutOfBoundsException aioobe){
	    throw new com.bbn.openmap.util.HandleError(aioobe);
	} catch (NumberFormatException nfe){
	    throw new com.bbn.openmap.util.HandleError(nfe);
	} catch (ClassCastException cce){
	    throw new com.bbn.openmap.util.HandleError(cce);
	} catch (NullPointerException npe) {
	    throw new com.bbn.openmap.util.HandleError(npe);
	} catch (java.security.AccessControlException ace) {
	    throw new com.bbn.openmap.util.HandleError(ace);
	}

	Debug.message("csvlocation",
		      "CSVLocationHandler | Finished File:" + locationFile + 
		      ", read " + lineCount + " locations");

	try {	      
	    if (streamReader != null) {
		streamReader.close();
	    }
	} catch(java.io.IOException ioe) {
	    throw new com.bbn.openmap.util.HandleError(ioe);
	}
		
	if (lineCount == 0 && readHeader) {
	    Debug.output("CSVLocationHandler has read file, but didn't find any data.\n  Check file for a header line, and make sure that the\n  properties (csvFileHasHeader) is set properly for this CSVLocationHandler. Trying again without header...");
	    csvHasHeader = !csvHasHeader;
	    return createData();
	}

	return qt;
    }

    /**
     * When a new Location object needs to be created from data read
     * in the CSV file, this method is called.  This method lets you
     * extend the CSVLocationLayer and easily set what kind of
     * Location objects to use.
     * @param lat latitude of location, decimal degrees.
     * @param lon longitude of location, decimal degrees.
     * @param name the label of the location.
     * @param iconURL the String for a URL for an icon.  Can be null.
     * @return Location object for lat/lon/name/iconURL.
     */
    protected Location createLocation(float lat, float lon, String name, String iconURL) {

	// This will turn into a regular location if iconURL is null.
	Location loc = new URLRasterLocation(lat, lon, name, iconURL);

	// let the layer handler default set these initially...
	loc.setShowName(isShowNames());
	loc.setShowLocation(isShowLocations());

	loc.setLocationHandler(this);
	loc.setLocationPaint(getLocationColor());
	loc.getLabel().setLinePaint(getNameColor());
	loc.setDetails(name + " is at lat: " + lat + ", lon: " + lon);

	if (iconURL != null) {
	    loc.setDetails(loc.getDetails() + " icon: " + iconURL);
	}

	Debug.message("csvlocation", "CSVLocationHandler " + loc.getDetails());

	return loc;
    }

    /**  
     * @param ranFile the file to be read.  The file pointer shoutd be
     * set to the line you want read.
     * @return Array of strings representing the values between the
     * commas.
     * */
    protected String[] readCSVLineFromFile(BufferedReader ranFile, 
					   String[] retPaths) {
	if (ranFile != null) {

	    try {
		String newLine = ranFile.readLine();
		if (newLine == null) return null;
		StringTokenizer token = new StringTokenizer(newLine, ",");
		int numPaths = token.countTokens();

		if (retPaths == null) {
		    retPaths = new String[numPaths];
		} else numPaths = retPaths.length;
		for (int i = 0; i < numPaths; i++){
		    retPaths[i] = token.nextToken();
		}		    
	    } catch (java.io.IOException ioe) {
		return null;
	    } catch (java.util.NoSuchElementException nsee){
		Debug.output("CSVLocationHandler: readCSVLineFromFile: oops");
	    }
	}
	return retPaths;
    }

    /**
     * Prepares the graphics for the layer.  This is where the
     * getRectangle() method call is made on the location.  <p>
     * Occasionally it is necessary to abort a prepare call.  When
     * this happens, the map will set the cancel bit in the
     * LayerThread, (the thread that is running the prepare).  If this
     * Layer needs to do any cleanups during the abort, it should do
     * so, but return out of the prepare asap.
     *
     */
    public Vector get(float nwLat, float nwLon, float seLat, float seLon, 
		      Vector graphicList) {
	
	// IF the quadtree has not been set up yet, do it!
	if (quadtree == null){
	    Debug.output("CSVLocationHandler: Figuring out the locations and names! (This is a one-time operation!)");
	    quadtree = createData();
	}

	if (quadtree != null) {
	    if (Debug.debugging("csvlocation")) {
		Debug.output("CSVLocationHandler|CSVLocationHandler.get() ul.lon = "
				   + nwLon + " lr.lon = " + seLon +
				   " delta = " + (seLon - nwLon)); 
	    }

	    quadtree.get(nwLat, nwLon, seLat, seLon, graphicList);
	}
	return graphicList;
    }

    public void fillLocationPopUpMenu(LocationPopupMenu locMenu) {

	LocationCBMenuItem lcbi = new LocationCBMenuItem(LocationHandler.showname, 
							 locMenu, 
							 getLayer());
	lcbi.setState(locMenu.getLoc().isShowName());
	locMenu.add(lcbi);
	locMenu.add(new LocationMenuItem(showdetails, locMenu, getLayer()));
    }

    protected Box box = null;

   /** 
     * Provides the palette widgets to control the options of showing
     * maps, or attribute text.
     * 
     * @return Component object representing the palette widgets.
     */
    public Component getGUI() {
	if (box == null){
	    JCheckBox showCSVLocationCheck, showNameCheck;
	    JButton rereadFilesButton;
	    
	    showCSVLocationCheck = new JCheckBox("Show Locations", isShowLocations());
	    showCSVLocationCheck.setActionCommand(showLocationsCommand);
	    showCSVLocationCheck.addActionListener(this);
	    
	    showNameCheck = new JCheckBox("Show Location Names", isShowNames());
	    showNameCheck.setActionCommand(showNamesCommand);
	    showNameCheck.addActionListener(this);
	    
	    rereadFilesButton = new JButton("Reload Data From Source");
	    rereadFilesButton.setActionCommand(readDataCommand);
	    rereadFilesButton.addActionListener(this);
	    
	    box = Box.createVerticalBox();
	    box.add(showCSVLocationCheck);
	    box.add(showNameCheck);
	    box.add(rereadFilesButton);
	}
	return box;
    }

    //----------------------------------------------------------------------
    // ActionListener interface implementation
    //----------------------------------------------------------------------

    /** 
     * The Action Listener method, that reacts to the palette widgets
     * actions.
     */
    public void actionPerformed(ActionEvent e) {
	String cmd = e.getActionCommand();
	if (cmd == showLocationsCommand) {		
	    JCheckBox locationCheck = (JCheckBox)e.getSource();
	    setShowLocations(locationCheck.isSelected());	        
	    if(Debug.debugging("location")){
	    	Debug.output("CSVLocationHandler::actionPerformed showLocations is "
				   + isShowLocations());
	    }
	    getLayer().repaint();
	} else if (cmd == showNamesCommand) {
	    JCheckBox namesCheck = (JCheckBox)e.getSource();
	    setShowNames(namesCheck.isSelected());
	    if(Debug.debugging("location")){
	    	Debug.output("CSVLocationHandler::actionPerformed showNames is "
			     + isShowNames());
	    }

	    LocationLayer ll = getLayer();
	    if (namesCheck.isSelected() && 
		ll.getDeclutterMatrix() != null && 
		ll.getUseDeclutterMatrix()) {
		ll.doPrepare();
	    } else {
		ll.repaint();
	    }
	} else if (cmd == readDataCommand) {
	    Debug.output("Re-reading Locations file");
	    quadtree = null;
	    getLayer().doPrepare();
	} else 	{
	    Debug.error("Unknown action command \"" + cmd +
			       "\" in LocationLayer.actionPerformed().");
	}
    }

}
