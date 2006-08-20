package org.openmrs.web.servlet;


import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.DecimalFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;

import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.labels.StandardXYToolTipGenerator;

import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.NumberAxis;

import org.jfree.chart.axis.DateAxis;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.web.WebConstants;

public class ShowGraphServlet extends HttpServlet {

	public static final long serialVersionUID = 1231231L;
	private Log log = LogFactory.getLog(ShowGraphServlet.class);
	//private static final DateFormat Formatter = new SimpleDateFormat("MM/dd/yyyy");

	// Supported mime types
	private static final String PNG_MIME_TYPE = "image/png";
	private static final String JPG_MIME_TYPE = "image/jpeg";
	
		
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	
		try { 
			// TODO (jmiranda) Need better error handling
			Integer patientId = Integer.parseInt(request.getParameter("patientId"));
			Integer conceptId = Integer.parseInt(request.getParameter("conceptId"));
			Integer width = request.getParameter("width")!=null?Integer.parseInt(request.getParameter("width")):new Integer(500);
			Integer height = request.getParameter("width")!=null?Integer.parseInt(request.getParameter("height")) : new Integer(300);
			String mimeType = request.getParameter("mimeType")!=null?request.getParameter("mimeType"):PNG_MIME_TYPE;
			
			boolean userSpecifiedMaxRange = request.getParameter("maxRange")!=null;
			boolean userSpecifiedMinRange = request.getParameter("minRange")!=null;
			double maxRange = request.getParameter("maxRange")!=null?Double.parseDouble(request.getParameter("maxRange")):0.0;  
			double minRange = request.getParameter("minRange")!=null?Double.parseDouble(request.getParameter("minRange")):0.0;
			
			Context context = getContext(request);
			Patient patient = context.getPatientService().getPatient(patientId);
			Concept concept = context.getConceptService().getConcept(conceptId);
			
			Set<Obs> observations = new HashSet<Obs>();
			String chartTitle, rangeAxisTitle, domainAxisTitle = "";
			if (concept != null ) { 
				// Get observations
				observations = context.getObsService().getObservations(patient, concept);				
				chartTitle = concept.getName(request.getLocale()).getName();
				rangeAxisTitle = chartTitle;
			}
			else { 
				chartTitle = "Concept " + conceptId + " not found";
				rangeAxisTitle = "Value";
				
			}
			domainAxisTitle = "Date";
			
			// Create data set
			TimeSeries series = new TimeSeries(rangeAxisTitle, Day.class);
			TimeSeriesCollection dataset = new TimeSeriesCollection();
			Calendar calendar = Calendar.getInstance();
			for( Obs obs : observations ) { 
				if (obs.getValueNumeric() != null) {		// Shouldn't be needed but just in case
					calendar.setTime(obs.getObsDatetime());
					log.debug("Adding value: " + obs.getValueNumeric() + " for " + calendar.get(Calendar.MONTH) + "/" + calendar.get(Calendar.YEAR) );
					
					// Set range
					//if (obs.getValueNumeric().doubleValue() < minRange) 
					//	minRange = obs.getValueNumeric().doubleValue();
					
					//if (obs.getValueNumeric().doubleValue() > maxRange) 
					//	maxRange = obs.getValueNumeric().doubleValue();
								
					// Add data point to series
					Day day = new Day(
						calendar.get(Calendar.DAY_OF_MONTH),
						calendar.get(Calendar.MONTH)+1,			// January = 0 
						calendar.get(Calendar.YEAR)
					);
					series.addOrUpdate(day, obs.getValueNumeric());
				}
			}
			// Add series to dataset
			dataset.addSeries(series);

			// Create graph
			JFreeChart chart = ChartFactory.createTimeSeriesChart(
				chartTitle,
				domainAxisTitle,
				rangeAxisTitle,
				dataset,
				true, 
				true, 
				false
			);
			// Customize the plot (range and domain axes)
	        XYPlot plot = (XYPlot) chart.getPlot();		        
	        plot.setNoDataMessage("No Data Available");
	        // Add filled data points
			XYItemRenderer r = plot.getRenderer();
			if (r instanceof XYLineAndShapeRenderer) {
				XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;

				renderer.setBaseShapesFilled(true);
				renderer.setBaseShapesVisible(true);
				
				// Only works with image maps (requires some work to support) 
				/*
		        StandardXYToolTipGenerator g = new StandardXYToolTipGenerator(
		            StandardXYToolTipGenerator.DEFAULT_TOOL_TIP_FORMAT,
		            new SimpleDateFormat("MMM-yy"), 
		            new DecimalFormat("0.0")
		        );
		        renderer.setToolTipGenerator(g);
		        */
			}		
	        
	        // Modify x-axis (datetime)
	        DateAxis axis = (DateAxis) plot.getDomainAxis();
	        axis.setDateFormatOverride(new SimpleDateFormat("MMM-yy"));

	        // Set y-axis range (values)
	        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
	        
	        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        
	        if (userSpecifiedMinRange) { 
	        	minRange = ( rangeAxis.getLowerBound() < minRange ) ? rangeAxis.getLowerBound() : minRange; 
	        }
	        
	        if (userSpecifiedMaxRange) { // otherwise we just use default range
	        	maxRange = ( rangeAxis.getUpperBound() > maxRange ) ? rangeAxis.getUpperBound() : maxRange; 
		        //maxRange = maxRange + ((maxRange - minRange) * 0.1);	// add a buffer to the max
			}
	        rangeAxis.setRange(minRange, maxRange);

			// Modify response to disable caching
			response.setHeader("Pragma", "No-cache"); 
			response.setDateHeader("Expires", 0); 
			response.setHeader("Cache-Control", "no-cache");
			
			// Write chart out to response as image 
			try { 
				if ( JPG_MIME_TYPE.equalsIgnoreCase(mimeType) ) { 
					response.setContentType(JPG_MIME_TYPE);
					ChartUtilities.writeChartAsJPEG(response.getOutputStream(), chart, width, height);
				} 
				else if ( PNG_MIME_TYPE.equalsIgnoreCase(mimeType)) { 
					response.setContentType(PNG_MIME_TYPE);
					ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, width, height);	
				} 
				else { 
					// Throw exception: unsupported mime type
				}
			} catch (IOException e) { 
				log.error(e);
			}
		
		} 
		// Add error handling above and remove this try/catch 
		catch (Exception e) { 
			log.error(e);
		}
	}	
	
	/**
	 * 
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {	
		doGet(request,response);		
	}
	
	
	/**
	 * Convenience method to get context from session.  
	 * 
	 * TODO Should probably be added to some helper class since it is used all of the time. 
	 */
	public Context getContext(HttpServletRequest request) throws ServletException { 
		HttpSession session = request.getSession();
		Context context = (Context)session.getAttribute(WebConstants.OPENMRS_CONTEXT_HTTPSESSION_ATTR);
		if (context == null) {
			throw new ServletException("Requires a valid context");
		}	
		return context;
	}

}
