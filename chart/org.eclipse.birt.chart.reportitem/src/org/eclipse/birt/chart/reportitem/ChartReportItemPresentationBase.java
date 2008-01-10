/***********************************************************************
 * Copyright (c) 2007 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Actuate Corporation - initial API and implementation
 ***********************************************************************/

package org.eclipse.birt.chart.reportitem;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.birt.chart.api.ChartEngine;
import org.eclipse.birt.chart.computation.withaxes.ScaleContext;
import org.eclipse.birt.chart.device.EmptyUpdateNotifier;
import org.eclipse.birt.chart.device.IDeviceRenderer;
import org.eclipse.birt.chart.device.IImageMapEmitter;
import org.eclipse.birt.chart.exception.ChartException;
import org.eclipse.birt.chart.factory.GeneratedChartState;
import org.eclipse.birt.chart.factory.Generator;
import org.eclipse.birt.chart.factory.IDataRowExpressionEvaluator;
import org.eclipse.birt.chart.factory.RunTimeContext;
import org.eclipse.birt.chart.log.ILogger;
import org.eclipse.birt.chart.log.Logger;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.attribute.Bounds;
import org.eclipse.birt.chart.reportitem.i18n.Messages;
import org.eclipse.birt.chart.reportitem.plugin.ChartReportItemPlugin;
import org.eclipse.birt.chart.script.ChartScriptContext;
import org.eclipse.birt.chart.script.ScriptHandler;
import org.eclipse.birt.chart.util.ChartUtil;
import org.eclipse.birt.chart.util.PluginSettings;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.engine.api.EngineConstants;
import org.eclipse.birt.report.engine.api.HTMLRenderContext;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.extension.IBaseResultSet;
import org.eclipse.birt.report.engine.extension.ICubeResultSet;
import org.eclipse.birt.report.engine.extension.IQueryResultSet;
import org.eclipse.birt.report.engine.extension.ReportItemPresentationBase;
import org.eclipse.birt.report.engine.extension.Size;
import org.eclipse.birt.report.model.api.ExtendedItemHandle;
import org.eclipse.birt.report.model.api.ModuleUtil;
import org.eclipse.birt.report.model.api.extension.ExtendedElementException;
import org.eclipse.birt.report.model.api.extension.IReportItem;
import org.eclipse.birt.report.model.elements.interfaces.IReportItemModel;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Base presentation implementation for Chart. This class can be extended for
 * various implementation.
 */
public class ChartReportItemPresentationBase extends ReportItemPresentationBase
{

	protected InputStream fis = null;

	protected String imageMap = null;

	protected String sExtension = null;

	private String sSupportedFormats = null;

	private String outputFormat = null;

	private int outputType = -1;

	protected Chart cm = null;

	protected IDeviceRenderer idr = null;

	protected ExtendedItemHandle handle;

	protected RunTimeContext rtc = null;

	private static List registeredDevices = null;

	protected static ILogger logger = Logger.getLogger( "org.eclipse.birt.chart.reportitem/trace" ); //$NON-NLS-1$

	static
	{
		registeredDevices = new ArrayList( );
		try
		{
			String[][] formats = PluginSettings.instance( )
					.getRegisteredOutputFormats( );
			for ( int i = 0; i < formats.length; i++ )
			{
				registeredDevices.add( formats[i][0] );
			}
		}
		catch ( ChartException e )
		{
			logger.log( e );
		}
	}

	/**
	 * check if the format is supported by the browser and device renderer.
	 */
	private boolean isOutputRendererSupported( String format )
	{
		if ( format != null )
		{
			if ( sSupportedFormats != null
					&& ( sSupportedFormats.indexOf( format ) != -1 ) )
			{
				return registeredDevices.contains( format );
			}
		}
		return false;
	}

	private String getFirstSupportedFormat( String formats )
	{
		if ( formats != null && formats.length( ) > 0 )
		{
			int idx = formats.indexOf( ';' );
			if ( idx == -1 )
			{
				if ( isOutputRendererSupported( formats ) )
				{
					return formats;
				}
			}
			else
			{
				String ext = formats.substring( 0, idx );

				if ( isOutputRendererSupported( ext ) )
				{
					return ext;
				}
				else
				{
					return getFirstSupportedFormat( formats.substring( idx + 1 ) );
				}
			}
		}

		// PNG as default.
		return "PNG"; //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#setModelObject(org.eclipse.birt.report.model.api.ExtendedItemHandle)
	 */
	public void setModelObject( ExtendedItemHandle eih )
	{
		IReportItem item = getReportItem( eih );
		if ( item == null )
		{
			return;
		}
		cm = (Chart) ( (ChartReportItemImpl) item ).getProperty( ChartReportItemUtil.PROPERTY_CHART );
		handle = eih;

		Object of = handle.getProperty( ChartReportItemUtil.PROPERTY_OUTPUT );
		if ( of instanceof String )
		{
			outputFormat = (String) of;
		}

		of = ( (ChartReportItemImpl) item ).getProperty( ChartReportItemUtil.PROPERTY_SCALE );
		if ( of instanceof ScaleContext )
		{
			if ( rtc == null )
			{
				rtc = new RunTimeContext( );
			}
			rtc.setScale( (ScaleContext) of );
		}
	}

	protected IReportItem getReportItem( ExtendedItemHandle eih )
	{
		IReportItem item = null;
		try
		{
			item = eih.getReportItem( );
		}
		catch ( ExtendedElementException e )
		{
			logger.log( e );
		}
		if ( item == null )
		{
			try
			{
				eih.loadExtendedElement( );
				item = eih.getReportItem( );
			}
			catch ( ExtendedElementException eeex )
			{
				logger.log( eeex );
			}
			if ( item == null )
			{
				logger.log( ILogger.ERROR,
						Messages.getString( "ChartReportItemPresentationImpl.log.UnableToLocateWrapper" ) ); //$NON-NLS-1$
			}
		}
		return item;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#setLocale(java.util.Locale)
	 */
	public final void setLocale( Locale lcl )
	{
		if ( rtc == null )
		{
			rtc = new RunTimeContext( );
		}
		rtc.setLocale( lcl );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#setOutputFormat(java.lang.String)
	 */
	public void setOutputFormat( String sOutputFormat )
	{
		if ( sOutputFormat.equalsIgnoreCase( "HTML" ) ) //$NON-NLS-1$
		{
			if ( isOutputRendererSupported( outputFormat ) )
			{
				sExtension = outputFormat;
			}
			else if ( outputFormat != null
					&& outputFormat.toUpperCase( ).equals( "GIF" ) && //$NON-NLS-1$ 
					isOutputRendererSupported( "PNG" ) ) //$NON-NLS-1$
			{
				// render old GIF charts as PNG
				sExtension = "PNG"; //$NON-NLS-1$
			}
			else if ( isOutputRendererSupported( "SVG" ) ) //$NON-NLS-1$
			{
				// SVG is the preferred output for HTML
				sExtension = "SVG"; //$NON-NLS-1$
			}
			else
			{
				sExtension = getFirstSupportedFormat( sSupportedFormats );
			}
		}
		else if ( sOutputFormat.equalsIgnoreCase( "PDF" ) ) //$NON-NLS-1$
		{
			if ( outputFormat != null
					&& outputFormat.toUpperCase( ).equals( "SVG" ) ) //$NON-NLS-1$
			{
				// Since engine doesn't support embedding SVG, always embed PNG
				sExtension = "PNG"; //$NON-NLS-1$
			}
			else if ( isOutputRendererSupported( outputFormat ) )
			{
				sExtension = outputFormat;
			}
			else if ( isOutputRendererSupported( "PNG" ) ) //$NON-NLS-1$
			{
				// PNG is the preferred output for PDF
				sExtension = "PNG"; //$NON-NLS-1$
			}
			else
			{
				sExtension = getFirstSupportedFormat( sSupportedFormats );
			}
		}
		else
		{
			if ( isOutputRendererSupported( outputFormat ) )
			{
				sExtension = outputFormat;
			}
			else
			{
				sExtension = getFirstSupportedFormat( sSupportedFormats );
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#setSupportedImageFormats(java.lang.String)
	 */
	public void setSupportedImageFormats( String sSupportedFormats )
	{
		this.sSupportedFormats = sSupportedFormats;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#deserialize(java.io.InputStream)
	 */
	public void deserialize( InputStream is )
	{
		try
		{
			ObjectInputStream ois = new ObjectInputStream( is ) {

				// Fix compatibility bug: the class ChartScriptContext is moved
				// from package
				// "org.eclipse.birt.chart.internal.script" to package
				// "org.eclipse.birt.chart.script", which causes the stored
				// instances of
				// ChartScriptContext can't be de-serialized.
				protected Class resolveClass( ObjectStreamClass desc )
						throws IOException, ClassNotFoundException
				{
					if ( "org.eclipse.birt.chart.internal.script.ChartScriptContext".equals( desc.getName( ) ) )
					{
						return ChartScriptContext.class;
					}
					return super.resolveClass( desc );
				}
			};
			Object o = ois.readObject( );

			if ( o instanceof RunTimeContext )
			{
				RunTimeContext drtc = (RunTimeContext) o;

				if ( rtc != null )
				{
					drtc.setULocale( rtc.getULocale( ) );
					drtc.setScale( rtc.getScale( ) );
				}

				rtc = drtc;
				cm = rtc.getScriptContext( ).getChartInstance( );
				// Set back the cm into the handle from the engine, so that the
				// chart inside the
				// reportdesignhandle is the same as the one used during
				// presentation.
				// No command should be executed, since it's a runtime operation
				// Set the model directly through setModel and not setProperty
				if ( cm != null && handle != null )
				{
					IReportItem item = handle.getReportItem( );
					( (ChartReportItemImpl) item ).setModel( cm );
					( (ChartReportItemImpl) item ).setScale( rtc.getScale( ) );
				}

				// Get chart max row number from application context
				Object oMaxRow = context.getAppContext( )
						.get( EngineConstants.PROPERTY_EXTENDED_ITEM_MAX_ROW );
				if ( oMaxRow != null )
				{
					rtc.putState( ChartUtil.CHART_MAX_ROW, oMaxRow );
				}
				else
				{
					// Get chart max row number from global variables if app
					// context doesn't put it
					oMaxRow = context.getGlobalVariable( EngineConstants.PROPERTY_EXTENDED_ITEM_MAX_ROW );
					if ( oMaxRow != null )
					{
						rtc.putState( ChartUtil.CHART_MAX_ROW, oMaxRow );
					}
				}
			}
			ois.close( );
		}
		catch ( Exception e )
		{
			logger.log( e );
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#getOutputType()
	 */
	public int getOutputType( )
	{
		if ( outputType == -1 )
		{
			if ( "SVG".equals( sExtension ) || "SWF".equals( sExtension ) ) //$NON-NLS-1$ //$NON-NLS-2$
			{
				outputType = OUTPUT_AS_IMAGE;
			}
			else
			{
				outputType = OUTPUT_AS_IMAGE_WITH_MAP;
			}
		}
		return outputType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#getImageMIMEType()
	 */
	public String getImageMIMEType( )
	{
		return idr.getMimeType( );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#getOutputContent()
	 */
	public Object getOutputContent( )
	{
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#finish()
	 */
	public void finish( )
	{
		logger.log( ILogger.INFORMATION,
				Messages.getString( "ChartReportItemPresentationImpl.log.finishStart" ) ); //$NON-NLS-1$

		// CLOSE THE TEMP STREAM PROVIDED TO THE CALLER
		try
		{
			// clean up the image map.
			imageMap = null;

			if ( fis != null )
			{
				fis.close( );
				fis = null;
			}
		}
		catch ( IOException ioex )
		{
			logger.log( ioex );
		}

		// Dispose renderer resources
		if ( idr != null )
		{
			idr.dispose( );
			idr = null;
		}

		logger.log( ILogger.INFORMATION,
				Messages.getString( "ChartReportItemPresentationImpl.log.finishEnd" ) ); //$NON-NLS-1$
	}

	protected Bounds computeBounds( )
	{
		// Standard computation for chart bounds
		final Bounds originalBounds = cm.getBlock( ).getBounds( );

		// we must copy the bounds to avoid that setting it on one object
		// unsets it on its precedent container

		Bounds bounds = (Bounds) EcoreUtil.copy( originalBounds );
		return bounds;
	}

	protected IDataRowExpressionEvaluator createEvaluator( IBaseResultSet set )
	{
		if ( set instanceof IQueryResultSet )
		{
			// Here, we must use chart model to check if grouping is defined. we
			// can't use grouping definitions in IQueryResultSet to check it,
			// because maybe chart inherits data set from container and the data
			// set contains grouping, but chart doesn't define grouping.
			if ( ChartReportItemUtil.canContainGrouping( cm ) )
			{
				return new BIRTGroupedQueryResultSetEvaluator( (IQueryResultSet) set,
						ChartReportItemUtil.hasAggregation( cm ) );
			}
			return new BIRTQueryResultSetEvaluator( (IQueryResultSet) set );
		}
		else if ( set instanceof ICubeResultSet )
		{
			if ( ChartReportItemUtil.canContainGrouping( cm ) )
			{
				return new BIRTGroupedCubeResultSetEvaluator( (ICubeResultSet) set );
			}
			return new BIRTCubeResultSetEvaluator( (ICubeResultSet) set );
		}
		return null;
	}

	protected ScaleContext createSharedScale( IBaseResultSet baseResultSet )
			throws BirtException
	{
		Object min = baseResultSet.evaluate( "row._outer[\"" //$NON-NLS-1$
				+ ChartReportItemUtil.QUERY_MIN
				+ "\"]" ); //$NON-NLS-1$
		Object max = baseResultSet.evaluate( "row._outer[\"" //$NON-NLS-1$
				+ ChartReportItemUtil.QUERY_MAX
				+ "\"]" ); //$NON-NLS-1$
		return ScaleContext.createSimpleScale( min, max );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#getSize()
	 */
	public Size getSize( )
	{
		if ( cm != null )
		{
			logger.log( ILogger.INFORMATION,
					Messages.getString( "ChartReportItemPresentationImpl.log.getSizeStart" ) ); //$NON-NLS-1$
			final Size sz = new Size( );
			sz.setWidth( (float) cm.getBlock( ).getBounds( ).getWidth( ) );
			sz.setHeight( (float) cm.getBlock( ).getBounds( ).getHeight( ) );
			sz.setUnit( Size.UNITS_PT );
			logger.log( ILogger.INFORMATION,
					Messages.getString( "ChartReportItemPresentationImpl.log.getSizeEnd" ) ); //$NON-NLS-1$
			return sz;
		}
		return super.getSize( );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.extension.IReportItemPresentation#onRowSets(org.eclipse.birt.report.engine.extension.IRowSet[])
	 */
	public Object onRowSets( IBaseResultSet[] baseResultSet )
			throws BirtException
	{
		// Extract result set to render and check for null data
		IBaseResultSet resultSet = getDataToRender( baseResultSet );

		// Skip gracefully if there is no data
		if ( resultSet == null )
		{
			return null;
		}

		try
		{
			// Create shared scale if needed
			if ( rtc.getScale( ) == null
					&& ChartReportItemUtil.canScaleShared( handle, cm ) )
			{
				rtc.setScale( createSharedScale( resultSet ) );
			}

			// Update chart model if needed
			updateChartModel( );

			// Get the BIRT report context
			BIRTExternalContext externalContext = new BIRTExternalContext( context );

			// Initialize script handler and register birt context in scope
			initializeScriptHandler( externalContext );

			// Prepare Data for Series
			IDataRowExpressionEvaluator rowAdapter = createEvaluator( resultSet );

			// Prepare data processor for hyperlinks/tooltips
			BIRTActionEvaluator evaluator = new BIRTActionEvaluator( );

			// Bind Data to series
			Generator.instance( ).bindData( rowAdapter, evaluator, cm, rtc );

			// Prepare Device Renderer
			prepareDeviceRenderer( );

			// Build the chart
			GeneratedChartState gcs = buildChart( rowAdapter, externalContext );

			// Render the chart
			renderToImageFile( gcs );

			// Close the dataRow evaluator. It needs to stay opened until the
			// chart is fully rendered.
			rowAdapter.close( );

			// Set the scale shared when scale has been computed, and store it
			// in the ReportItem
			if ( rtc.getScale( ) != null && !rtc.getScale( ).isShared( ) )
			{
				rtc.getScale( ).setShared( true );
				( (ChartReportItemImpl) getReportItem( handle ) ).setScale( rtc.getScale( ) );
			}

			// Returns the content to display (image or image+imagemap)
			return getImageToDisplay( );
		}
		catch ( BirtException birtException )
		{
			// Check if the exception is caused by no data to display (in that
			// case skip gracefully)
			if ( isNoDataException( birtException ) )
			{
				return null;
			}
			else
			{
				throw birtException;
			}
		}
		catch ( RuntimeException ex )
		{
			logger.log( ILogger.ERROR,
					Messages.getString( "ChartReportItemPresentationImpl.log.onRowSetsFailed" ) ); //$NON-NLS-1$
			logger.log( ex );
			throw new ChartException( ChartReportItemPlugin.ID,
					ChartException.GENERATION,
					ex );
		}
	}

	/**
	 * Check there is some data to display in the chart.
	 * 
	 * @param baseResultSet
	 * @return null if nothing to render
	 */
	private IBaseResultSet getDataToRender( IBaseResultSet[] baseResultSet )
			throws BirtException
	{
		// BIND RESULTSET TO CHART DATASETS
		if ( baseResultSet == null || baseResultSet.length < 1 )
		{
			// if the Data rows are null/empty, just log it and returns
			// null gracefully.
			logger.log( ILogger.INFORMATION,
					Messages.getString( "ChartReportItemPresentationImpl.error.NoData" ) ); //$NON-NLS-1$
			return null;
		}

		IBaseResultSet resultSet = baseResultSet[0];
		if ( resultSet == null || ChartReportItemUtil.isEmpty( resultSet ) )
		{
			// Do nothing when IBaseResultSet is empty or null
			return null;
		}

		logger.log( ILogger.INFORMATION,
				Messages.getString( "ChartReportItemPresentationImpl.log.onRowSetsStart" ) ); //$NON-NLS-1$

		// catch unwanted null handle case
		if ( handle == null )
		{
			assert false; // should we throw an exception here instead?
			return null;
		}
		return resultSet;
	}

	private void initializeScriptHandler( BIRTExternalContext externalContext )
			throws ChartException
	{
		String javaHandlerClass = handle.getEventHandlerClass( );
		if ( javaHandlerClass != null && javaHandlerClass.length( ) > 0 )
		{
			// use java handler if available.
			cm.setScript( javaHandlerClass );
		}

		rtc.setScriptClassLoader( new BIRTScriptClassLoader( appClassLoader ) );
		// INITIALIZE THE SCRIPT HANDLER
		// UPDATE THE CHART SCRIPT CONTEXT

		ScriptHandler sh = rtc.getScriptHandler( );

		if ( sh == null ) // IF NOT PREVIOUSLY DEFINED BY
		// REPORTITEM ADAPTER
		{
			sh = new ScriptHandler( );
			rtc.setScriptHandler( sh );

			sh.setScriptClassLoader( rtc.getScriptClassLoader( ) );
			sh.setScriptContext( rtc.getScriptContext( ) );

			final String sScriptContent = cm.getScript( );
			if ( externalContext != null
					&& externalContext.getScriptable( ) != null )
			{
				sh.init( externalContext.getScriptable( ) );
			}
			else
			{
				sh.init( null );
			}
			sh.setRunTimeModel( cm );

			if ( sScriptContent != null
					&& sScriptContent.length( ) > 0
					&& rtc.isScriptingEnabled( ) )
			{
				sh.register( ModuleUtil.getScriptUID( handle.getPropertyHandle( IReportItemModel.ON_RENDER_METHOD ) ),
						sScriptContent );
			}
		}
	}

	protected GeneratedChartState buildChart(
			IDataRowExpressionEvaluator rowAdapter,
			BIRTExternalContext externalContext ) throws ChartException
	{
		final Bounds bo = computeBounds( );

		initializeRuntimeContext( rowAdapter );

		return Generator.instance( ).build( idr.getDisplayServer( ),
				cm,
				bo,
				externalContext,
				rtc,
				new ChartReportStyleProcessor( handle, this.style ) );
	}

	protected Object getImageToDisplay( )
	{
		if ( getOutputType( ) == OUTPUT_AS_IMAGE )
		{
			return fis;
		}
		else if ( getOutputType( ) == OUTPUT_AS_IMAGE_WITH_MAP )
		{
			return new Object[]{
					fis, imageMap
			};
		}
		else
			throw new IllegalArgumentException( );
	}

	protected void renderToImageFile( GeneratedChartState gcs )
			throws ChartException
	{
		logger.log( ILogger.INFORMATION,
				Messages.getString( "ChartReportItemPresentationImpl.log.onRowSetsRendering" ) ); //$NON-NLS-1$

		ByteArrayOutputStream baos = new ByteArrayOutputStream( );
		BufferedOutputStream bos = new BufferedOutputStream( baos );

		idr.setProperty( IDeviceRenderer.FILE_IDENTIFIER, bos );
		idr.setProperty( IDeviceRenderer.UPDATE_NOTIFIER,
				new EmptyUpdateNotifier( cm, gcs.getChartModel( ) ) );

		Generator.instance( ).render( idr, gcs );

		// RETURN A STREAM HANDLE TO THE NEWLY CREATED IMAGE
		try
		{
			bos.close( );
			fis = new ByteArrayInputStream( baos.toByteArray( ) );
		}
		catch ( Exception ioex )
		{
			throw new ChartException( ChartReportItemPlugin.ID,
					ChartException.GENERATION,
					ioex );
		}

		if ( getOutputType( ) == OUTPUT_AS_IMAGE_WITH_MAP )
		{
			imageMap = ( (IImageMapEmitter) idr ).getImageMap( );
		}

	}

	private boolean isNoDataException( BirtException birtException )
	{
		Throwable ex = birtException;
		while ( ex.getCause( ) != null )
		{
			ex = ex.getCause( );
		}

		if ( ex instanceof ChartException
				&& ( (ChartException) ex ).getType( ) == ChartException.ZERO_DATASET )
		{
			// if the Data set has zero lines, just
			// returns null gracefully.
			return true;
		}

		if ( ex instanceof ChartException
				&& ( (ChartException) ex ).getType( ) == ChartException.ALL_NULL_DATASET )
		{
			// if the Data set contains all null values, just
			// returns null gracefully and render nothing.
			return true;
		}

		if ( ( ex instanceof ChartException && ( (ChartException) ex ).getType( ) == ChartException.INVALID_IMAGE_SIZE ) )
		{
			// if the image size is invalid, this may caused by
			// Display=None, lets ignore it.
			logger.log( birtException );
			return true;
		}

		logger.log( ILogger.ERROR,
				Messages.getString( "ChartReportItemPresentationImpl.log.onRowSetsFailed" ) ); //$NON-NLS-1$
		logger.log( birtException );
		return false;

	}

	private void initializeRuntimeContext(
			IDataRowExpressionEvaluator rowAdapter )
	{
		rtc.setActionRenderer( new BIRTActionRenderer( this.handle,
				this.ah,
				rowAdapter,
				this.context ) );
		rtc.setMessageLookup( new BIRTMessageLookup( context ) );
		Object renderContext = context.getAppContext( )
				.get( EngineConstants.APPCONTEXT_HTML_RENDER_CONTEXT );

		// read RtL flag from engine
		if ( renderContext instanceof HTMLRenderContext )
		{
			IRenderOption renderOption = ( (HTMLRenderContext) renderContext ).getRenderOption( );
			if ( renderOption instanceof HTMLRenderOption )
			{
				if ( ( (HTMLRenderOption) renderOption ).getHtmlRtLFlag( ) )
				{
					rtc.setRightToLeft( true );
				}
			}
		}

	}

	protected void prepareDeviceRenderer( ) throws ChartException
	{
		idr = ChartEngine.instance( ).getRenderer( "dv." //$NON-NLS-1$
				+ sExtension.toUpperCase( Locale.US ) );

		idr.setProperty( IDeviceRenderer.DPI_RESOLUTION, new Integer( dpi ) );

		if ( "SVG".equalsIgnoreCase( sExtension ) ) //$NON-NLS-1$
		{
			idr.setProperty( "resize.svg", Boolean.TRUE ); //$NON-NLS-1$
		}

	}

	/**
	 * Updates chart model when something needs change
	 */
	protected void updateChartModel( )
	{
		// Do nothing
	}
}
