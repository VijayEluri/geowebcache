package org.geowebcache.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.ServletUtils;

public class Demo {
    
    public static void makeMap(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker, 
            String action, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException {

        String page = null;

        // Do we have a layer, or should we make a list?
        if (action != null) {
            String layerName = ServletUtils.URLDecode(action, request.getCharacterEncoding());
            
            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);

            String rawGridSet = request.getParameter("gridSet");
            String gridSetStr = null;
            if(rawGridSet != null)
                gridSetStr = ServletUtils.URLDecode(rawGridSet, request.getCharacterEncoding());
            
            if(gridSetStr == null) {
                gridSetStr = request.getParameter("srs");
                
                if(gridSetStr == null) {
                    gridSetStr = layer.getGridSubsets().keys().nextElement();
                }
            }
            
            String formatStr = request.getParameter("format");

            if (formatStr != null) {
                if (!layer.supportsFormat(formatStr)) {
                    throw new GeoWebCacheException(
                            "Unknow or unsupported format " + formatStr);
                }
            } else {
                formatStr = layer.getDefaultMimeType().getFormat();
            }
            
            if(request.getPathInfo().startsWith("/demo")) {
                // Running in GeoServer
                page = generateHTML(layer, gridSetStr, formatStr, true);
            } else {
                page = generateHTML(layer, gridSetStr, formatStr, false);
            }
            

        } else {
            if(request.getRequestURI().endsWith("/")) {
                try {
                    String reqUri = request.getRequestURI();
                    response.sendRedirect(response.encodeRedirectURL(reqUri.substring(0, reqUri.length() - 1)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            } else {
                page = generateHTML(tileLayerDispatcher, gridSetBroker);
            }
            
        }
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        try {
            response.getOutputStream().write(page.getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("failed to render HTML");
        }
    }
    
    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker) 
    throws GeoWebCacheException {
        String reloadPath = "rest/reload";

        String header = 
            "<html>\n"+ServletUtils.gwcHtmlHeader("GWC Demos") +"<body>\n"
            + ServletUtils.gwcHtmlLogoLink("")
            +"<table>\n"
            +"<table cellspacing=\"10\" border=\"0\">\n"
            +"<tr><td><strong>Layer name:</strong></td>\n" 
            +"<td><strong>Grids Sets:</strong></td>\n"  
            +"</tr>\n";
            
        
        String rows = tableRows(tileLayerDispatcher, gridSetBroker);
        
        String footer = "</table>\n"
            +"<br />"
            +"<strong>These are just quick demos. GeoWebCache also supports:</strong><br />\n"
            +"<ul><li>WMTS, TMS, Virtual Earth and Google Maps</li>\n"
            +"<li>Proxying GetFeatureInfo, GetLegend and other WMS requests</li>\n"
            +"<li>Advanced request and parameter filters</li>\n"
            +"<li>Output format adjustments, such as compression level</li>\n"
            +"<li>Adjustable expiration headers and automatic cache expiration</li>\n"
            +"<li>RESTful interface for seeding and configuration (beta)</li>\n"
            +"</ul>\n"
            +"<br />\n"
            +"<strong>Reload Configuration:</strong><br />\n"
            +"<p>You can reload the configuration by pressing the following button. " 
            +"The username / password is configured in WEB-INF/user.properties, or the admin " 
            +" user in GeoServer if you are using the plugin.</p>\n"
            +"<form form id=\"kill\" action=\""+reloadPath+"\" method=\"post\">"
            +"<input type=\"hidden\" name=\"reload_configuration\"  value=\"1\" />"
            +"<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Reload Configuration\"></span>"
            +"</form>"
            + "</body></html>";
        
        return header + rows + footer;
    }
    
    private static String tableRows(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker)
    throws GeoWebCacheException {
        StringBuffer buf = new StringBuffer();
        
        Map<String,TileLayer> layerList = tileLayerDispatcher.getLayers();
        TreeSet<String> keys = new TreeSet<String>(layerList.keySet());

        Iterator<String> it = keys.iterator();
        while(it.hasNext()) {
            TileLayer layer = layerList.get(it.next());
            
            buf.append("<tr><td style=\"min-width: 100px;\"><strong>"+layer.getName() + "</strong><br />\n");
            buf.append("<a href=\"rest/seed/"+layer.getName()+"\">Seed this layer</a>\n");
            buf.append("</td>");
            buf.append("<td><table width=\"100%\">");
            
            int count = 0;
            Iterator<GridSubset> iter = layer.getGridSubsets().values().iterator();
            while(iter.hasNext()) {
                GridSubset gridSubset = iter.next();
                String gridSetName = gridSubset.getName();
                if(gridSetName.length() > 20) {
                    gridSetName = gridSetName.substring(0, 20) + "...";
                }
                buf.append("<tr><td style=\"width: 170px;\">").append(gridSetName);
                
                buf.append("</td><td>OpenLayers: [");
                Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
                boolean prependComma = false;
                while(mimeIter.hasNext()) {
                    MimeType mime = mimeIter.next();
                    if(mime instanceof ImageMime) {
                        if(prependComma) {
                            buf.append(", ");
                        } else {
                            prependComma = true;
                        }
                        buf.append(generateDemoUrl(layer.getName(), gridSubset.getName(), (ImageMime) mime));
                    }
                }
                buf.append("]</td><td>\n");
                
                if(gridSubset.getName().equals(gridSetBroker.WORLD_EPSG4326.getName())) {
                    buf.append(" &nbsp; KML: [");
                    String prefix = "";
                    prependComma = false;
                    Iterator<MimeType> kmlIter = layer.getMimeTypes().iterator();
                    while(kmlIter.hasNext()) {
                        MimeType mime = kmlIter.next();
                        if(mime instanceof ImageMime || mime == XMLMime.kml) {
                            if(prependComma) {
                                buf.append(", ");
                            } else {
                                prependComma = true;
                            }
                            buf.append("<a href=\""+prefix+"service/kml/"+layer.getName()+"."+mime.getFileExtension()+".kml\">"+mime.getFileExtension()+"</a>");
                        } else if(mime == XMLMime.kmz) {
                            if(prependComma) {
                                buf.append(", ");
                            } else {
                                prependComma = true;
                            }
                            buf.append("<a href=\""+prefix+"service/kml/"+layer.getName()+".kml.kmz\">kmz</a>");
                        }
                    }
                    buf.append("]");
                } else {
                    // No Google Earth support
                }
                buf.append("</td></tr>");
                count++;
            }
            
            //if(count == 0) {
            //    buf.append("<tr><td colspan=\"2\"><i>None</i></td></tr>\n");
            //}
        
            buf.append("</table></td>\n");            
            buf.append("</tr>\n");
        }
        
        return buf.toString();
    }
    
    private static String generateDemoUrl(String layerName, String gridSetId, ImageMime imageMime) {
        return "<a href=\"demo/"+layerName+"?gridSet="+gridSetId+"&format="+imageMime.getFormat()+"\">"
            +imageMime.getFileExtension()+"</a>";
    }
    
    private static String generateHTML(TileLayer layer, String gridSetStr, String formatStr, boolean asPlugin) 
    throws GeoWebCacheException {
        String layerName = layer.getName();
        
        GridSubset gridSubset = layer.getGridSubset(gridSetStr);
        
        BoundingBox bbox = gridSubset.getGridSetBounds();
        BoundingBox zoomBounds = gridSubset.getOriginalExtent();
        
        String res = "resolutions: " + Arrays.toString(gridSubset.getResolutions()) + ",\n";
        
        String units = "units: \""+gridSubset.getGridSet().guessMapUnits()+"\",\n";

        String openLayersPath;
        if(asPlugin) {
            openLayersPath = "../../openlayers/OpenLayers.js";
        } else {
            openLayersPath = "../openlayers/OpenLayers.js";
        }
        
        
        String page =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
            +"<meta http-equiv=\"imagetoolbar\" content=\"no\">\n"
            +"<title>"+layerName+" "+gridSubset.getName()+" "+formatStr+"</title>\n"
            +"<style type=\"text/css\">\n"
            +"body { font-family: sans-serif; font-weight: bold; font-size: .8em; }\n"
            +"body { border: 0px; margin: 0px; padding: 0px; }\n"
            +"#map { width: 85%; height: 85%; border: 0px; padding: 0px; }\n"
            +"</style>\n"

            +"<script src=\""+openLayersPath+"\"></script>\n"
            +"<script type=\"text/javascript\">\n"
            +"OpenLayers.DOTS_PER_INCH = "+gridSubset.getDotsPerInch()+";\n"
            +"OpenLayers.Util.onImageLoadErrorColor = 'transparent';\n"
            +"var map, layer;\n"
        		
            +"function init(){\n"
            +"var mapOptions = { \n"
            + res
            +"projection: new OpenLayers.Projection('"+gridSubset.getSRS().toString()+"'),\n"
            +"maxExtent: new OpenLayers.Bounds("+bbox.toString()+"),\n"
            + units
	    +"controls: []\n"
	    +"};\n"
            +"map = new OpenLayers.Map('map', mapOptions );\n"
	    +"map.addControl(new OpenLayers.Control.PanZoomBar({\n"
	    +"		position: new OpenLayers.Pixel(2, 15)\n"
	    +"}));\n"
	    +"map.addControl(new OpenLayers.Control.Navigation());\n"
	    +"map.addControl(new OpenLayers.Control.Scale($('scale')));\n"
	    +"map.addControl(new OpenLayers.Control.MousePosition({element: $('location')}));\n"
            +"var demolayer = new OpenLayers.Layer.WMS(\n"
            +"\""+layerName+"\",\"../service/wms\",\n"
            +"{layers: '"+layerName+"', format: '"+formatStr+"' },\n"
            +"{ tileSize: new OpenLayers.Size("+gridSubset.getTileWidth()+","+gridSubset.getTileHeight()+") }\n"	
            + ");\n"
            +"map.addLayer(demolayer);\n"
            +"map.zoomToExtent(new OpenLayers.Bounds("+zoomBounds.toString()+"));\n"
            +"// The following is just for GetFeatureInfo, which is not cached. Most people do not need this \n"
            +"map.events.register('click', map, function (e) {\n"
            +"  document.getElementById('nodelist').innerHTML = \"Loading... please wait...\";\n"
            +"  var params = {\n"
            +"    REQUEST: \"GetFeatureInfo\",\n"
            +"    EXCEPTIONS: \"application/vnd.ogc.se_xml\",\n"
            +"    BBOX: map.getExtent().toBBOX(),\n"
            +"    X: e.xy.x,\n"
            +"    Y: e.xy.y,\n"
            +"    INFO_FORMAT: 'text/html',\n"
            +"    QUERY_LAYERS: map.layers[0].params.LAYERS,\n"
            +"    FEATURE_COUNT: 50,\n"
            +"    Layers: '"+layerName+"',\n"
            +"    Styles: '',\n"
            +"    Srs: '"+gridSubset.getSRS().toString()+"',\n"
            +"    WIDTH: map.size.w,\n"
            +"    HEIGHT: map.size.h,\n"
            +"    format: \""+formatStr+"\" };\n"
            +"  OpenLayers.loadURL(\"../service/wms\", params, this, setHTML, setHTML);\n"
            +"  OpenLayers.Event.stop(e);\n"
            +"  });\n"
            +"}\n"
            +"function setHTML(response){\n"
            +"    document.getElementById('nodelist').innerHTML = response.responseText;\n"
            +"};\n"
            +"</script>\n"
            +"</head>\n"
            +"<body onload=\"init()\">\n"
            +"<div id=\"map\"></div>\n"
            +"<div id=\"nodelist\"></div>\n"
            +"</body>\n"
            +"</html>";
        return page;
    }
}
