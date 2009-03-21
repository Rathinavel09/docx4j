package org.docx4j.convert.out.pdf.viaIText;

import java.awt.Dimension;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.docx4j.fonts.Mapper;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.Body;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RPr;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

public class Conversion extends org.docx4j.convert.out.pdf.PdfConversion {
	
	public Conversion(WordprocessingMLPackage wordMLPackage) {
		super(wordMLPackage);
	}
		

	/** Create a pdf version of the document, using XSL FO. 
	 * 
	 * @param os
	 *            The OutputStream to write the pdf to 
	 * 
	 * */     
    public void output(OutputStream os) throws Docx4JException {
    	
    	Document pdfDoc = new Document();
    	
    	try {
    		
    		PdfWriter.getInstance(pdfDoc, os);
    		pdfDoc.open();
    		
    		org.docx4j.wml.Document wmlDocumentEl 
    			= (org.docx4j.wml.Document)wordMLPackage.getMainDocumentPart().getJaxbElement();
    		Body body =  wmlDocumentEl.getBody();
    		List <Object> bodyChildren = body.getEGBlockLevelElts();
    		
    		traverseBlockLevelContent( bodyChildren, pdfDoc );    		
        	  
    	} catch (Exception e) {
    		throw new Docx4JException("iTextissues", e);
    	} finally {
			// Clean-up
			try {
				pdfDoc.close();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
		
	}
    
	void traverseBlockLevelContent(List <Object> children, Document pdfDoc) throws Exception {

		
		for (Object o : children ) {
						
			log.debug("object: " + o.getClass().getName() );
			
			if (o instanceof org.docx4j.wml.P) {
				
				org.docx4j.wml.P p = (org.docx4j.wml.P) o;
		
//				if (p.getPPr() != null && p.getPPr().getPStyle() != null) {
//				}
		
//				if (p.getPPr() != null && p.getPPr().getRPr() != null) {
//				}
		
				Paragraph pdfParagraph = new Paragraph();
				getRunContent( p.getParagraphContent(), pdfDoc, pdfParagraph);
				pdfDoc.add(pdfParagraph);
		
			} else if (o instanceof org.docx4j.wml.SdtBlock) {

				org.docx4j.wml.SdtBlock sdt = (org.docx4j.wml.SdtBlock) o;				
				// Don't bother looking in SdtPr				
				traverseBlockLevelContent(sdt.getSdtContent().getEGContentBlockContent(),
						pdfDoc);
				
//			} else if (o instanceof org.docx4j.wml.SdtContentBlock) {
//
//				org.docx4j.wml.SdtBlock sdt = (org.docx4j.wml.SdtBlock) o;
//				
//				// Don't bother looking in SdtPr
//				
//				traverseMainDocumentRecursive(sdt.getSdtContent().getEGContentBlockContent(),
//						fontsDiscovered, stylesInUse);
				
			} else if (o instanceof org.w3c.dom.Node) {
				
				// If Xerces is on the path, this will be a org.apache.xerces.dom.NodeImpl;
				// otherwise, it will be com.sun.org.apache.xerces.internal.dom.ElementNSImpl;
				
				// Ignore these, eg w:bookmarkStart
				
				log.debug("not traversing into unhandled Node: " + ((org.w3c.dom.Node)o).getNodeName() );
				
			} else if ( o instanceof javax.xml.bind.JAXBElement) {

				log.debug( "Encountered " + ((JAXBElement) o).getDeclaredType().getName() );
					
//				if (((JAXBElement) o).getDeclaredType().getName().equals(
//						"org.docx4j.wml.P")) {
//					org.docx4j.wml.P p = (org.docx4j.wml.P) ((JAXBElement) o)
//							.getValue();
				
			} else {
				log.error( "UNEXPECTED: " + o.getClass().getName() );
			} 
		}
	}

	void getRunContent(List<Object> children, Document pdfDoc,
			Paragraph pdfParagraph) throws Exception {

		for (Object o : children) {
			log.debug("object: " + o.getClass().getName());

			if (o instanceof org.docx4j.wml.R) {

				org.docx4j.wml.R run = (org.docx4j.wml.R) o;

				Font font = null;
				int fontSize;

				if (run.getRPr() != null) {

					// TODO - follow style

					font = new Font(Font.TIMES_ROMAN); // TODO - FIXME

					if (run.getRPr().getSz() != null) {
						org.docx4j.wml.HpsMeasure hps = run.getRPr().getSz();
						font.setSize(hps.getVal().intValue() / 2);
					}

					if (run.getRPr().getB() != null
							&& run.getRPr().getB().isVal()
							&& run.getRPr().getI() != null
							&& run.getRPr().getI().isVal()) {
						font.setStyle(Font.BOLDITALIC);
					} else if (run.getRPr().getI() != null
							&& run.getRPr().getI().isVal()) {
						font.setStyle(Font.ITALIC);
					} else if (run.getRPr().getB() != null
							&& run.getRPr().getB().isVal()) {
						font.setStyle(Font.BOLD);
					}

				}

				List<Object> runContent = run.getRunContent();

				for (Object rc : runContent) {

					if (rc instanceof javax.xml.bind.JAXBElement) {

						log.debug("Encountered "
								+ ((JAXBElement) rc).getDeclaredType()
										.getName());

						if (((JAXBElement) rc).getDeclaredType().getName()
								.equals("org.docx4j.wml.Text")) {

							org.docx4j.wml.Text t = (org.docx4j.wml.Text) ((JAXBElement) rc)
									.getValue();

							if (font == null) {
								pdfParagraph.add(new Chunk(t.getValue()));
							} else {
								pdfParagraph.add(new Chunk(t.getValue(), font));
							}
							log.debug("Added content " + t.getValue());
						} else if (((JAXBElement) rc).getDeclaredType()
								.getName().equals("org.docx4j.wml.Drawing")) {

							org.docx4j.wml.Drawing drawing = (org.docx4j.wml.Drawing) ((JAXBElement) rc)
									.getValue();
							addDrawing(drawing, pdfDoc, pdfParagraph);

						} else {
							log.debug("What? Encountered "
									+ ((JAXBElement) rc).getDeclaredType()
											.getName());
							// eg org.docx4j.wml.R$LastRenderedPageBreak
						}

					} else if (rc instanceof org.docx4j.wml.Br) {

						pdfDoc.newPage();

					} else {

						log.debug("found in R: " + rc.getClass().getName());
					}
				}

			// } else if (o instanceof org.docx4j.wml.Drawing) {
			//				
			// addDrawing((Drawing)o, pdfDoc, pdfParagraph);

			} else if (o instanceof org.w3c.dom.Node) {

				log.debug("not traversing into unhandled Node: "
						+ ((org.w3c.dom.Node) o).getNodeName());

			} else if (o instanceof javax.xml.bind.JAXBElement) {

				log.debug("Encountered "
						+ ((JAXBElement) o).getDeclaredType().getName());

			} else {
				log.error("UNEXPECTED: " + o.getClass().getName());
			}
		}

	}

	void addDrawing(org.docx4j.wml.Drawing o,
			Document pdfDoc, Paragraph pdfParagraph) throws Exception {
	
		org.docx4j.wml.Drawing drawing = (org.docx4j.wml.Drawing) o;
		List<Object> list = drawing.getAnchorOrInline();
		if (list.size() != 1
			|| !(list.get(0) instanceof org.docx4j.dml.Inline)) {
			//There should not be an Anchor in 'list'
			//because it is not being supported and 
			//RunML.initChildren() prevents it from
			//being assigned to this InlineDrawingML object.
			//See: RunML.initChildren().
			throw new IllegalArgumentException("Unsupported Docx Object = " + o);			
		}
		
		org.docx4j.dml.Inline inline = (org.docx4j.dml.Inline) list.get(0);
//		if (inline.getExtent() != null) {
//			int cx = Long.valueOf(inline.getExtent().getCx()).intValue();
//			cx = StyleSheet.emuToPixels(cx);
//			int cy = Long.valueOf(inline.getExtent().getCy()).intValue();
//			cy = StyleSheet.emuToPixels(cy);
//			//this.extentInPixels = new Dimension(cx, cy);
//		}
		
		if (inline.getEffectExtent() != null) {
			//this.effectExtent = new CTEffectExtent(inline.getEffectExtent());
		}
		
		if (inline.getGraphic() != null) {
			
			byte[] imagedata = BinaryPartAbstractImage.getImage(wordMLPackage,
					inline.getGraphic() );
			Image img = Image.getInstance( imagedata );
			pdfDoc.add(img);
		}
	}	    
	    }
    
