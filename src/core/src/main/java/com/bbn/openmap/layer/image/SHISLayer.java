// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/plugin/shis/SHISPlugIn.java,v $
// $RCSfile: SHISPlugIn.java,v $
// $Revision: 1.5 $
// $Date: 2005/12/09 21:09:13 $
// $Author: dietrick $
// 
// **********************************************************************

package com.bbn.openmap.layer.image;

import java.awt.geom.Point2D;
import java.util.Properties;

import com.bbn.openmap.image.ImageServerConstants;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PropUtils;

/**
 * The SHISLayer asks for an image from a SimpleHttpImageServer. It has some
 * properties that you can set in the openmap.properties file:
 * <P>
 * The query to the SimpleHttpImageServer looks like something you can plug into
 * your browser to test the server:
 * <P>
 * http://hostname:port/path?REQUEST=MAP&amp;PROJTYPE=projection_type_value&amp;SCALE=
 * scale_value
 * &amp;LAT=center_latitude&amp;LON=center_longitude&amp;HEIGHT=map_pixel_height&amp;WIDTH
 * =map_pixel_width
 * &amp;FORMAT=image_format&amp;TRANSPARENT=true|false&amp;BGCOLOR=background_color
 * 
 * <P>
 * The projection information will be entered automatically by the layer based
 * on the projection it receives from the MapBean. The other parameters can be
 * entered in the properties for the layer.
 * 
 * <pre>
 * 
 * #For the SHISLayer
 * shislayer.class=com.bbn.openmap.layer.image.SHISLayer
 * shislayer.prettyName=Whatever you like
 * shislayer.host=hostname 
 * shislayer.port=port number 
 * shislayer.path=query path (default is openmap)
 * shislayer.format=image format (JPEG, GIF from WMTConstants.java) 
 * shislayer.transparent=true or false, depends on imageformat 
 * shislayer.backgroundColor=RGB hex string (RRGGBB)
 * 
 * </pre>
 */
public class SHISLayer extends WebImageLayer {

    protected String queryHeader = null;
    protected String imageFormat = null;
    protected String backgroundColor = null;
    protected String transparent = null;
    protected String host = null;
    protected String port = null;
    protected String path = null;

    public final static String HostNameProperty = "host";
    public final static String PortNumberProperty = "port";
    public final static String PathProperty = "path";
    public final static String ImageFormatProperty = "format";
    public final static String BackgroundColorProperty = "backgroundColor";
    public final static String TransparentProperty = "transparent";

    public SHISLayer() {
    }

    /**
     * When a projection is received, translate it into a valid request for a
     * SimpleHttpImageServer, and then return the image received back from it.
     * 
     * @param p projection of the screen, holding scale, center coords, height,
     *        width.
     * @return a string describing the request to sent to the SHIS.
     */
    public String createQueryString(Projection p) {

        if (queryHeader == null) {
            return null;
        }

        StringBuffer buf = new StringBuffer(queryHeader);
        buf.append(REQUEST).append("=").append(MAP).append("&");

        if (p != null) {
            Point2D center = p.getCenter();
            buf.append(PROJTYPE).append("=").append(p.getName()).append("&").append(SCALE).append("=").append(p.getScale()).append("&").append(LAT).append("=").append(center.getY()).append("&").append(LON).append("=").append(center.getX()).append("&").append(ImageServerConstants.HEIGHT).append("=").append(p.getHeight()).append("&").append(ImageServerConstants.WIDTH).append("=").append(p.getWidth());
        } else {
            buf.append(PROJTYPE).append("=name_undefined&").append(SCALE).append("=scale_undefined&").append(LAT).append("=center_lat_undefined&").append(LON).append("=center_lon_undefined&").append(ImageServerConstants.HEIGHT).append("=height_undefined&").append(ImageServerConstants.WIDTH).append("=width_undefined");
        }

        if (imageFormat != null) {
            buf.append("&").append(FORMAT).append("=").append(imageFormat);
        }

        if (transparent != null) {
            buf.append("&").append(TRANSPARENT).append("=true");
        }

        if (backgroundColor != null) {
            buf.append("&").append(BGCOLOR).append("=").append(backgroundColor);
        }

        String layers = getLayerMarkers();
        if (layers != null) {
            buf.append("&").append(layers);
        }

        return buf.toString();
    }

    public String getServerName() {
        return queryHeader;
    }

    public String getLayerMarkers() {
        // Not implemented - should be a list that can be set by the
        // user.
        return null;
    }

    /**
     * PropertyConsumer method.
     */
    public void setProperties(String prefix, Properties setList) {
        super.setProperties(prefix, setList);

        prefix = PropUtils.getScopedPropertyPrefix(prefix);

        host = setList.getProperty(prefix + HostNameProperty);
        port = setList.getProperty(prefix + PortNumberProperty);
        path = setList.getProperty(prefix + PathProperty);
        imageFormat = setList.getProperty(prefix + ImageFormatProperty);
        transparent = setList.getProperty(prefix + TransparentProperty);
        backgroundColor = setList.getProperty(prefix + BackgroundColorProperty);

        if (path == null) {
            path = com.bbn.openmap.Environment.OpenMapPrefix; // "openmap"
        }

        if (host == null || port == null) {
            Debug.error("SHISPlugIn needs a host name and port number for the image server.");
            queryHeader = null;
            return;
        }

        queryHeader = "http://" + (host == null ? "localhost" : host)
                + (port == null ? "" : (":" + port)) + "/" + path + "?";

        if (Debug.debugging("plugin")) {
            Debug.output("SHISPlugIn: set up with header \"" + queryHeader + "\"");
        }
    }

    /**
     * PropertyConsumer method.
     */
    public Properties getProperties(Properties getList) {
        getList = super.getProperties(getList);

        String prefix = PropUtils.getScopedPropertyPrefix(this);
        getList.put(prefix + HostNameProperty, PropUtils.unnull(host));
        getList.put(prefix + PortNumberProperty, PropUtils.unnull(port));
        getList.put(prefix + PathProperty, PropUtils.unnull(path));
        getList.put(prefix + ImageFormatProperty, PropUtils.unnull(imageFormat));
        getList.put(prefix + TransparentProperty, PropUtils.unnull(transparent));
        getList.put(prefix + BackgroundColorProperty, PropUtils.unnull(backgroundColor));
        return getList;
    }

    /**
     * PropertyConsumer method.
     */
    public Properties getPropertyInfo(Properties list) {
        list = super.getPropertyInfo(list);

        list.put(initPropertiesProperty, HostNameProperty + " " + PortNumberProperty + " "
                + PathProperty + " " + ImageFormatProperty + " " + TransparentProperty + " "
                + BackgroundColorProperty);

        list.put(HostNameProperty, "This hostname of the server machine.");
        list.put(PortNumberProperty, "The port number the server is running on.");
        list.put(PathProperty, "The path to the server (openmap is default)");
        list.put(ImageFormatProperty, "Image format (JPEG|GIF|PPM|PNG)");
        list.put(TransparentProperty, "Whether background of image should be transparent.");
        list.put(TransparentProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");

        list.put(BackgroundColorProperty, "Background color for image.");
        list.put(BackgroundColorProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.ColorPropertyEditor");
        return list;
    }
}