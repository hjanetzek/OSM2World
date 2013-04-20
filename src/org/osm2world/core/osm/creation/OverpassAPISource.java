// This software is released into the Public Domain.  See osmosis-xml copying.txt for details.
package org.osm2world.core.osm.creation;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.openstreetmap.osmosis.core.OsmosisRuntimeException;
import org.openstreetmap.osmosis.core.container.v0_6.BoundContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.util.MultiMemberGZIPInputStream;
import org.openstreetmap.osmosis.xml.common.ElementProcessor;
import org.openstreetmap.osmosis.xml.v0_6.impl.OsmElementProcessor;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An OSM data source reading from an osm-xml file from the
 * OpenStreetMap-server.
 * 
 * @author <a href="mailto:Marcus@Wolschon.biz">Marcus Wolschon</a>
 */
public class OverpassAPISource implements RunnableSource {

	// private static final String OVERPASS_API =
	// "http://overpass-api.de/api/interpreter";

	private static final String OVERPASS_API = "http://city.informatik.uni-bremen.de/oapi/interpreter";
	/**
	 * The http-response-code for OK.
	 */
	private static final int RESPONSECODE_OK = 200;

	/**
	 * My logger for debug- and error-output.
	 */
	private static Logger log = Logger.getLogger(OverpassAPISource.class
			.getName());

	/**
	 * The timeout we use for the HttpURLConnection.
	 */
	private static final int TIMEOUT = 15000;

	/**
	 * Where to deliver the loaded data.
	 */
	private Sink mySink;

	/**
	 * Left longitude of the bounding box.
	 */
	private double myLeft;

	/**
	 * Right longitude of the bounding box.
	 */
	private double myRight;

	/**
	 * Top latitude of the bounding box.
	 */
	private double myTop;

	/**
	 * Bottom latitude of the bounding box.
	 */
	private double myBottom;

	/**
	 * The base url of the server. Defaults to.
	 * "http://www.openstreetmap.org/api/0.5".
	 */
	private String myBaseUrl = OVERPASS_API;

	/**
	 * The http connection used to retrieve data.
	 */
	private HttpURLConnection myActiveConnection;

	/**
	 * The stream providing response data.
	 */
	private InputStream responseStream;

	private String query;

	/**
	 * Creates a new instance with the specified geographical coordinates.
	 * 
	 * @param left
	 *            The longitude marking the left edge of the bounding box.
	 * @param right
	 *            The longitude marking the right edge of the bounding box.
	 * @param top
	 *            The latitude marking the top edge of the bounding box.
	 * @param bottom
	 *            The latitude marking the bottom edge of the bounding box.
	 * @param baseUrl
	 *            (optional) The base url of the server (eg.
	 *            http://www.openstreetmap.org/api/0.5).
	 */
	public OverpassAPISource(final double left, final double right,
			final double top, final double bottom, final String baseUrl,
			final String query) {
		this.myLeft = Math.min(left, right);
		this.myRight = Math.max(left, right);
		this.myTop = Math.max(top, bottom);
		this.myBottom = Math.min(top, bottom);
		if (baseUrl != null) {
			this.myBaseUrl = baseUrl;
		}
		String bbox = "(" + Math.min(top, bottom) + "," + Math.min(left, right)
				+ "," + Math.max(top, bottom) + "," + Math.max(left, right)
				+ ")";

		this.query = query.replaceAll("\\{\\{bbox\\}\\}", bbox);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSink(final Sink aSink) {
		this.mySink = aSink;

	}

	/**
	 * Cleans up any resources remaining after completion.
	 */
	private void cleanup() {
		if (myActiveConnection != null) {
			try {
				myActiveConnection.disconnect();
			} catch (Exception e) {
				log.log(Level.SEVERE, "Unable to disconnect.", e);
			}
			myActiveConnection = null;
		}

		if (responseStream != null) {
			try {

				responseStream.close();
			} catch (IOException e) {
				log.log(Level.SEVERE, "Unable to close response stream.", e);
			}
			responseStream = null;
		}
	}

	/**
	 * Creates a new SAX parser.
	 * 
	 * @return The newly created SAX parser.
	 */
	private SAXParser createParser() {
		try {
			return SAXParserFactory.newInstance().newSAXParser();

		} catch (ParserConfigurationException e) {
			throw new OsmosisRuntimeException("Unable to create SAX Parser.", e);
		} catch (SAXException e) {
			throw new OsmosisRuntimeException("Unable to create SAX Parser.", e);
		}
	}

	/**
	 * Reads all data from the server and send it to the {@link Sink}.
	 */
	public void run() {
		try {
			mySink.initialize(Collections.<String, Object> emptyMap());

			String encoded = URLEncoder.encode(query, "utf-8");
			// encoded = encoded.replaceAll("\\.", "%2E");
			// encoded = encoded.replaceAll("-", "%2D");
			System.out.println(myBaseUrl + "?data=" + encoded);

			SAXParser parser = createParser();
			InputStream inputStream = getInputStream(myBaseUrl + "?data="
					+ encoded);
			// data=[out:json]

			// First send the Bound down the pipeline
			mySink.process(new BoundContainer(new Bound(myRight, myLeft, myTop,
					myBottom, myBaseUrl)));
			try {
				parser.parse(inputStream, new Handler(mySink, false));
			} finally {
				inputStream.close();
				inputStream = null;
			}

			mySink.complete();

		} catch (SAXParseException e) {
			throw new OsmosisRuntimeException("Unable to parse xml"
					+ ".  publicId=(" + e.getPublicId() + "), systemId=("
					+ e.getSystemId() + "), lineNumber=" + e.getLineNumber()
					+ ", columnNumber=" + e.getColumnNumber() + ".", e);
		} catch (SAXException e) {
			throw new OsmosisRuntimeException("Unable to parse XML.", e);
		} catch (IOException e) {
			throw new OsmosisRuntimeException("Unable to read XML.", e);
		} finally {
			mySink.release();

			cleanup();
		}
	}

	/**
	 * Open a connection to the given url and return a reader on the input
	 * stream from that connection.
	 * 
	 * @param pUrlStr
	 *            The exact url to connect to.
	 * @return An reader reading the input stream (servers answer) or
	 *         <code>null</code>.
	 * @throws IOException
	 *             on io-errors
	 */
	private InputStream getInputStream(final String pUrlStr) throws IOException {
		URL url;
		int responseCode;
		String encoding;

		url = new URL(pUrlStr);
		myActiveConnection = (HttpURLConnection) url.openConnection();

		myActiveConnection.setRequestProperty("Accept-Encoding",
				"gzip, deflate");

		responseCode = myActiveConnection.getResponseCode();

		if (responseCode != RESPONSECODE_OK) {
			String message;
			String apiErrorMessage;

			apiErrorMessage = myActiveConnection.getHeaderField("Error");

			if (apiErrorMessage != null) {
				message = "Received API HTTP response code " + responseCode
						+ " with message \"" + apiErrorMessage
						+ "\" for URL \"" + pUrlStr + "\".";
			} else {
				message = "Received API HTTP response code " + responseCode
						+ " for URL \"" + pUrlStr + "\".";
			}

			throw new OsmosisRuntimeException(message);
		}

		myActiveConnection.setConnectTimeout(TIMEOUT);

		encoding = myActiveConnection.getContentEncoding();

		responseStream = myActiveConnection.getInputStream();
		if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
			responseStream = new MultiMemberGZIPInputStream(responseStream);
		} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			responseStream = new InflaterInputStream(responseStream,
					new Inflater(true));
		}

		return responseStream;
	}

	public class Handler extends DefaultHandler {

		// private static final Logger LOG =
		// Logger.getLogger(OsmHandler.class.getName());
		private static final String ELEMENT_NAME_OSM = "osm";
		private ElementProcessor osmElementProcessor;
		private ElementProcessor elementProcessor;
		private Locator documentLocator;

		public Handler(Sink osmSink, boolean enableDateParsing) {
			this.osmElementProcessor = new OsmElementProcessor(null, osmSink,
					false, false);
		}

		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			if (this.elementProcessor != null) {
				this.elementProcessor = this.elementProcessor.getChild(uri,
						localName, qName);
			} else if ("osm".equals(qName)) {
				this.elementProcessor = this.osmElementProcessor;
			} else {
				throw new OsmosisRuntimeException(
						"This does not appear to be an OSM XML file.");
			}

			this.elementProcessor.begin(attributes);
		}

		public void endElement(String uri, String localName, String qName) {
			this.elementProcessor.end();

			this.elementProcessor = this.elementProcessor.getParent();
		}

		public void setDocumentLocator(Locator documentLocator) {
			this.documentLocator = documentLocator;
		}

		public void error(SAXParseException e) throws SAXException {
			// LOG.severe("Unable to parse xml file.  publicId=(" +
			// this.documentLocator.getPublicId()
			// + "), systemId=(" + this.documentLocator.getSystemId() +
			// "), lineNumber="
			// + this.documentLocator.getLineNumber() + ", columnNumber="
			// + this.documentLocator.getColumnNumber() + ".");

			super.error(e);
		}
	}

}
