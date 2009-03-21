package org.docx4j.convert.out.pdf.viaXSLFO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.docx4j.XmlUtils;
import org.docx4j.convert.out.pdf.PdfConversion;
import org.docx4j.convert.out.xmlPackage.XmlPackage;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.NodeIterator;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.fo.FOEventHandler;
import org.apache.fop.fonts.FontTriplet;
import org.apache.log4j.Logger;
import org.apache.xml.dtm.ref.DTMNodeProxy;

public class Conversion extends org.docx4j.convert.out.pdf.PdfConversion {
	
	protected static Logger log = Logger.getLogger(Conversion.class);	
	
	public Conversion(WordprocessingMLPackage wordMLPackage) {
		super(wordMLPackage);
	}
	
	// Get the xslt file - Works in Eclipse - note absence of leading '/'
	static Source xslt;
	
	static {
		try {
			xslt = new StreamSource(
					org.docx4j.utils.ResourceUtils.getResource(
							"org/docx4j/convert/out/pdf/viaXSLFO/docx2fo.xslt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// TODO resolve WARN  [fop.apps.FOUserAgent] Glyph "Č" (0x10c, Ccaron) 
	// not available in font "Helvetica".


	/**
	 * Create a FOP font configuration for each font used in the
	 * document.
	 * 
	 * @return
	 */
	private String declareFonts() {
		
		StringBuffer result = new StringBuffer();
		Map fontsInUse = wordMLPackage.getMainDocumentPart().fontsInUse();
		Iterator fontMappingsIterator = fontsInUse.entrySet().iterator();
		while (fontMappingsIterator.hasNext()) {
		    Map.Entry pairs = (Map.Entry)fontMappingsIterator.next();
		    if(pairs.getKey()==null) {
		    	log.info("Skipped null key");
		    	pairs = (Map.Entry)fontMappingsIterator.next();
		    }
		    
		    String fontName = (String)pairs.getKey();
		    
		    
		    PhysicalFont pf = wordMLPackage.getFontMapper().getFontMappings().get(fontName);
		    
		    if (pf==null) {
		    	log.error("Document font " + fontName + " is not mapped to a physical font!");
		    	continue;
		    }
		    
		    result.append("<font embed-url=\"" +pf.getEmbeddedFile() + "\">" );
		    	// now add the first font triplet
			    FontTriplet fontTriplet = (FontTriplet)pf.getEmbedFontInfo().getFontTriplets().get(0);
			    result.append("<font-triplet name=\"" + fontTriplet.getName() + "\""
		    							+ " style=\"" + fontTriplet.getStyle() + "\""
		    							+ " weight=\"" + weightToCSS2FontWeight(fontTriplet.getWeight()) + "\""
		    									+ "/>" );		    		    
		    result.append("</font>" );
		}
		
		return result.toString();
		
	}
	
	private String weightToCSS2FontWeight(int i) {
		
		if (i>=700) {
			return "bold";
		} else {
			return "normal";
		}
		
	}
	
	/** Create a pdf version of the document, using XSL FO. 
	 * 
	 * @param os
	 *            The OutputStream to write the pdf to 
	 * 
	 * */     
    public void output(OutputStream os) throws Docx4JException {
    	
    	// See http://xmlgraphics.apache.org/fop/0.95/embedding.html
    	// (reuse if you plan to render multiple documents!)
    	FopFactory fopFactory = FopFactory.newInstance();
    	
    	try {
                
    	  DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
    	  String myConfig = "<fop version=\"1.0\"><strict-configuration>true</strict-configuration>" +
	  		"<renderers><renderer mime=\"application/pdf\">" +
	  		"<fonts>" + declareFonts() +  
	  		//<directory>/home/dev/fonts</directory>" +
	  		//"<directory>/usr/share/fonts/truetype/ttf-lucida</directory>" +
	  		//"<directory>/var/lib/defoma/fontconfig.d/D</directory>" +
	  		//"<directory>/var/lib/defoma/fontconfig.d/L</directory>" +
//	  		"<auto-detect/>" +
	  		"</fonts></renderer></renderers></fop>";
    	  
    	  log.debug("Using config: " + myConfig);
    			  
    	  	// See FOP's PrintRendererConfigurator
//    	  String myConfig = "<fop version=\"1.0\"><strict-configuration>true</strict-configuration>" +
//	  		"<renderers><renderer mime=\"application/pdf\">" +
//	  		"<fonts><directory recursive=\"true\">C:\\WINDOWS\\Fonts</directory>" +
//	  		"<auto-detect/>" +
//	  		"</fonts></renderer></renderers></fop>";
    	  
    	  
    	  Configuration cfg = cfgBuilder.build(
    			  new ByteArrayInputStream(myConfig.getBytes()) );
    	  fopFactory.setUserConfig(cfg);
    	  
    	  Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, os);
    	  
    	  Document domDoc = XmlPackage.getFlatDomDocument(wordMLPackage);	
    	  
    	  java.util.HashMap<String, Object> settings = new java.util.HashMap<String, Object>();
			settings.put("wmlPackage", wordMLPackage);
	      	  // Resulting SAX events (the generated FO) must be piped through to FOP
	      	  Result result = new SAXResult(fop.getDefaultHandler());
	    	  
	      	  log.info(System.getProperty("os.name"));
	      	  
	  		if (System.getProperty("os.name").toLowerCase().indexOf("windows")>-1) {
		      	/* On Windows, I'm getting 
		      	 * 
		      	21.03.2009 22:31:45 *ERROR* FOTreeBuilder: javax.xml.transform.TransformerException: java.lang.ArrayIndexOutOfBoundsException: 273 (FOTreeBuilder.java, line 201)
		      	Saved C:\Documents and Settings\Jason Harrop\My Documents\Downloads\AUMS.docx.pdf
		      	SystemId Unknown; Line #163; Column #-1; java.lang.ArrayIndexOutOfBoundsException: 273
		      	
		      	It doesn't seem to matter whether we're using Xerces or Crimson though.
				  		
		  		So on Windows, do 2 transforms :-(
		  		
		  		Which doesn't fix the problem, but show it to be:
		  		 
				 java.lang.ArrayIndexOutOfBoundsException: 273
				       at org.apache.fop.fo.StaticPropertyList.get(StaticPropertyList.java:70)
				       at org.apache.fop.fo.PropertyList.get(PropertyList.java:155)
				       at org.apache.fop.fo.flow.Block.bind(Block.java:133)
				       at org.apache.fop.fo.FObj.processNode(FObj.java:123)
				       at org.apache.fop.fo.FOTreeBuilder$MainFOHandler.startElement(FOTreeBuilder.java:282)
				       at org.apache.fop.fo.FOTreeBuilder.startElement(FOTreeBuilder.java:171)
				       at org.apache.xalan.transformer.TransformerIdentityImpl.startElement(TransformerIdentityImpl.java:1072)
				       at com.bluecast.xml.Piccolo.reportStartTag(Piccolo.java:1082)
				       at com.bluecast.xml.PiccoloLexer.parseOpenTagNS(PiccoloLexer.java:1471)
				       at com.bluecast.xml.PiccoloLexer.parseTagNS(PiccoloLexer.java:1360)
				       at com.bluecast.xml.PiccoloLexer.parseXMLNS(PiccoloLexer.java:1291)
				       at com.bluecast.xml.PiccoloLexer.parseXML(PiccoloLexer.java:1259)
				       at com.bluecast.xml.PiccoloLexer.yylex(PiccoloLexer.java:4716)
				       at com.bluecast.xml.Piccolo.yylex(Piccolo.java:1290)
				       at com.bluecast.xml.Piccolo.yyparse(Piccolo.java:1400)
				       at com.bluecast.xml.Piccolo.parse(Piccolo.java:714)
				       at org.apache.xalan.transformer.TransformerIdentityImpl.transform(TransformerIdentityImpl.java:484)		  		 
		      	 */

	  			ByteArrayOutputStream intermediate = new ByteArrayOutputStream();
	  			Result intermediateResult =  new StreamResult( intermediate );
	  			
	  			XmlUtils.transform(domDoc, xslt, settings, intermediateResult);
	  			
	  			String fo = intermediate.toString("UTF-8");
	  			log.info(fo);
	  			
	  			Source src = new StreamSource(new StringReader(fo));
		    	
	  			Transformer transformer = XmlUtils.tfactory.newTransformer();
	  			transformer.transform(src, result);
	  		} else {
	      	   
	      	  // Uncomment this line to see the generated formatting objects
//	    		Result result =
//	  			new javax.xml.transform.stream.StreamResult(System.out);		
	    	  XmlUtils.transform(domDoc, xslt, settings, result);
	  			
	  		}
    	  
    	} catch (Exception e) {
    		throw new Docx4JException("FOP issues", e);
    	} finally {
    	  //Clean-up
    	  try {
			os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}		
		
	}
	
    public static DocumentFragment createBlockForPPr( 
    		WordprocessingMLPackage wmlPackage,
    		NodeIterator pPrNodeIt,
    		String pStyleVal, NodeIterator childResults ) {
    	
    	// Note that this is invoked for every paragraph with a pPr node.
    	
    	// incoming objects are org.apache.xml.dtm.ref.DTMNodeIterator 
    	// which implements org.w3c.dom.traversal.NodeIterator

    	
    	log.info("style '" + pStyleVal );    	
    	log.info("pPrNode:" + pPrNodeIt.getClass().getName() ); // org.apache.xml.dtm.ref.DTMNodeIterator    	
    	log.info("childResults:" + childResults.getClass().getName() ); 
    	
    	
        try {
        	
        	// Get the pPr node as a JAXB object,
        	// so we can read it using our standard
        	// methods.  Its a bit sad that we 
        	// can't just adorn our DOM tree with the
        	// original JAXB objects?
			Unmarshaller u = Context.jc.createUnmarshaller();			
			u.setEventHandler(new org.docx4j.jaxb.JaxbValidationEventHandler());
			Object jaxb = u.unmarshal(pPrNodeIt.nextNode());
			
			PPr pPr = null;
			try {
				pPr =  (PPr)jaxb;
			} catch (ClassCastException e) {
		    	log.error("Couldn't cast " + jaxb.getClass().getName() + " to PPr!");
			}        	
        	
            // Create a DOM builder and parse the fragment			
        	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();        
			Document document = factory.newDocumentBuilder().newDocument();
			
			log.info("Document: " + document.getClass().getName() );

			Node foBlockElement = document.createElementNS("http://www.w3.org/1999/XSL/Format", "fo:block");			
			document.appendChild(foBlockElement);
			
			if (pPr==null) {
				Text err = document.createTextNode( "Couldn't cast " + jaxb.getClass().getName() + " to PPr!" );
				foBlockElement.appendChild(err);
				
			} else {
			       
				if ( pPr.getJc()!=null) {				
					((Element)foBlockElement).setAttribute("text-align", 
							pPr.getJc().getVal().value() );
				}
				// TODO - other pPr props.
				
				// Our fo:block wraps whatever result tree fragment
				// our style sheet produced when it applied-templates
				// to the child nodes
				Node n = childResults.nextNode();
				
//				log.info("Node we are importing: " + n.getClass().getName() );
//				foBlockElement.appendChild(
//						document.importNode(n, true) );
				/*
				 * Node we'd like to import is of type org.apache.xml.dtm.ref.DTMNodeProxy
				 * which causes
				 * org.w3c.dom.DOMException: NOT_SUPPORTED_ERR: The implementation does not support the requested type of object or operation.
				 * 
				 * See http://osdir.com/ml/text.xml.xerces-j.devel/2004-04/msg00066.html
				 * 
				 * So instead of importNode, use 
				 */
				treeCopy( (DTMNodeProxy)n,  foBlockElement );
			
			}
			
			DocumentFragment docfrag = document.createDocumentFragment();
			docfrag.appendChild(document.getDocumentElement());

			return docfrag;
						
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.toString() );
			log.error(e);
		} 
    	
    	return null;
    	
    }

    public static DocumentFragment createBlockForRPr( 
    		WordprocessingMLPackage wmlPackage,
    		NodeIterator rPrNodeIt,
    		NodeIterator childResults ) {
    	
    	// Note that this is invoked for every paragraph with a pPr node.
    	
    	// incoming objects are org.apache.xml.dtm.ref.DTMNodeIterator 
    	// which implements org.w3c.dom.traversal.NodeIterator

    	
    	log.info("pPrNode:" + rPrNodeIt.getClass().getName() ); // org.apache.xml.dtm.ref.DTMNodeIterator    	
    	log.info("childResults:" + childResults.getClass().getName() ); 
    	
    	
        try {
        	
        	// Get the pPr node as a JAXB object,
        	// so we can read it using our standard
        	// methods.  Its a bit sad that we 
        	// can't just adorn our DOM tree with the
        	// original JAXB objects?
			Unmarshaller u = Context.jc.createUnmarshaller();			
			u.setEventHandler(new org.docx4j.jaxb.JaxbValidationEventHandler());
			Object jaxb = u.unmarshal(rPrNodeIt.nextNode());
			
			RPr rPr = null;
			try {
				rPr =  (RPr)jaxb;
			} catch (ClassCastException e) {
		    	log.error("Couldn't cast " + jaxb.getClass().getName() + " to PPr!");
			}        	
        	
            // Create a DOM builder and parse the fragment
        	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();        
			Document document = factory.newDocumentBuilder().newDocument();
			
			log.info("Document: " + document.getClass().getName() );

			Node foBlockElement = document.createElementNS("http://www.w3.org/1999/XSL/Format", "fo:block");			
			document.appendChild(foBlockElement);
			
			if (rPr==null) {
				Text err = document.createTextNode( "Couldn't cast " + jaxb.getClass().getName() + " to PPr!" );
				foBlockElement.appendChild(err);
				
			} else {
				
				RFonts rFonts = rPr.getRFonts();
				if (rFonts !=null ) {
					
					String font = rFonts.getAscii();
					log.debug("Font: " + font);
					((Element)foBlockElement).setAttribute("font-family", 
							font );
				}
				
			    
				// bold
				
				if ( rPr.getB()!=null ) {				
					((Element)foBlockElement).setAttribute("font-weight", 
							"bold" );
				}
				// TODO - other rPr props.
				
				// Our fo:block wraps whatever result tree fragment
				// our style sheet produced when it applied-templates
				// to the child nodes
				Node n = childResults.nextNode();
				treeCopy( (DTMNodeProxy)n,  foBlockElement );			
			}
			
			DocumentFragment docfrag = document.createDocumentFragment();
			docfrag.appendChild(document.getDocumentElement());

			return docfrag;
						
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.toString() );
			log.error(e);
		} 
    	
    	return null;
    	
    }
    
    
    private static void treeCopy( org.apache.xml.dtm.ref.DTMNodeProxy sourceNode, Node destParent ) {
    	
    	log.debug("node type" + sourceNode.getNodeType());
    	
            switch (sourceNode.getNodeType() ) {

            	case Node.DOCUMENT_NODE: // type 9
            
                    // recurse on each child
                    NodeList nodes = sourceNode.getChildNodes();
                    if (nodes != null) {
                        for (int i=0; i<nodes.getLength(); i++) {
                        	treeCopy((DTMNodeProxy)nodes.item(i), destParent);
                        }
                    }
                    break;
                case Node.ELEMENT_NODE:
                    
                    // Copy of the node itself
            		Node newChild = destParent.getOwnerDocument().createElementNS(
            				sourceNode.getNamespaceURI(), sourceNode.getLocalName() );                    
            		destParent.appendChild(newChild);
            		
            		// .. its attributes
                	NamedNodeMap atts = sourceNode.getAttributes();
                	for (int i = 0 ; i < atts.getLength() ; i++ ) {
                		
                		Attr attr = (Attr)atts.item(i);
                		
                		((Element)newChild).setAttributeNS(attr.getNamespaceURI(), 
                				attr.getLocalName(), attr.getValue() );
                		    		
                	}

                    // recurse on each child
                    NodeList children = sourceNode.getChildNodes();
                    if (children != null) {
                        for (int i=0; i<children.getLength(); i++) {
                        	treeCopy( (DTMNodeProxy)children.item(i), newChild);
                        }
                    }

                    break;

                case Node.TEXT_NODE:
                	Node textNode = destParent.getOwnerDocument().createTextNode(sourceNode.getNodeValue());       
                	destParent.appendChild(textNode);
                    break;

//                case Node.CDATA_SECTION_NODE:
//                    writer.write("<![CDATA[" +
//                                 node.getNodeValue() + "]]>");
//                    break;
//
//                case Node.COMMENT_NODE:
//                    writer.write(indentLevel + "<!-- " +
//                                 node.getNodeValue() + " -->");
//                    writer.write(lineSeparator);
//                    break;
//
//                case Node.PROCESSING_INSTRUCTION_NODE:
//                    writer.write("<?" + node.getNodeName() +
//                                 " " + node.getNodeValue() +
//                                 "?>");
//                    writer.write(lineSeparator);
//                    break;
//
//                case Node.ENTITY_REFERENCE_NODE:
//                    writer.write("&" + node.getNodeName() + ";");
//                    break;
//
//                case Node.DOCUMENT_TYPE_NODE:
//                    DocumentType docType = (DocumentType)node;
//                    writer.write("<!DOCTYPE " + docType.getName());
//                    if (docType.getPublicId() != null)  {
//                        System.out.print(" PUBLIC \"" +
//                            docType.getPublicId() + "\" ");
//                    } else {
//                        writer.write(" SYSTEM ");
//                    }
//                    writer.write("\"" + docType.getSystemId() + "\">");
//                    writer.write(lineSeparator);
//                    break;
            }
        }
    	
    }
    
