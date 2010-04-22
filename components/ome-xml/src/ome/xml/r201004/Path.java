/*
 * ome.xml.r201004.Path
 *
 *-----------------------------------------------------------------------------
 *
 *  Copyright (C) @year@ Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee,
 *      University of Wisconsin-Madison
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *-----------------------------------------------------------------------------
 */

/*-----------------------------------------------------------------------------
 *
 * THIS IS AUTOMATICALLY GENERATED CODE.  DO NOT MODIFY.
 * Created by callan via xsd-fu on 2010-04-22 16:29:38+0100
 *
 *-----------------------------------------------------------------------------
 */

package ome.xml.r201004;

import java.util.ArrayList;
import java.util.List;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ome.xml.r201004.enums.*;

public class Path extends Shape
{
	// -- Constants --

	public static final String NAMESPACE = "http://www.openmicroscopy.org/Schemas/ROI/2010-04";

	// -- Instance variables --

	// Property
	private String definition;

	// -- Constructors --

	/** Default constructor. */
	public Path()
	{
		super();
	}

	/** 
	 * Constructs Path recursively from an XML DOM tree.
	 * @param element Root of the XML DOM tree to construct a model object
	 * graph from.
	 * @throws EnumerationException If there is an error instantiating an
	 * enumeration during model object creation.
	 */
	public Path(Element element) throws EnumerationException
	{
		update(element);
	}

	/** 
	 * Updates Path recursively from an XML DOM tree. <b>NOTE:</b> No
	 * properties are removed, only added or updated.
	 * @param element Root of the XML DOM tree to construct a model object
	 * graph from.
	 * @throws EnumerationException If there is an error instantiating an
	 * enumeration during model object creation.
	 */
	public void update(Element element) throws EnumerationException
	{	
		super.update(element);
		String tagName = element.getTagName();
		if (!"Path".equals(tagName))
		{
			System.err.println(String.format(
					"WARNING: Expecting node name of Path got %s",
					tagName));
			// TODO: Should be its own Exception
			//throw new RuntimeException(String.format(
			//		"Expecting node name of Path got %s",
			//		tagName));
		}
		if (element.hasAttribute("Definition"))
		{
			// Attribute property Definition
			setDefinition(String.valueOf(
					element.getAttribute("Definition")));
		}
	}

	// -- Path API methods --


	// Property
	public String getDefinition()
	{
		return definition;
	}

	public void setDefinition(String definition)
	{
		this.definition = definition;
	}

	public Element asXMLElement(Document document)
	{
		return asXMLElement(document, null);
	}

	protected Element asXMLElement(Document document, Element Path_element)
	{
		// Creating XML block for Path
		if (Path_element == null)
		{
			Path_element =
					document.createElementNS(NAMESPACE, "Path");
		}

		if (definition != null)
		{
			// Attribute property Definition
			Path_element.setAttribute("Definition", definition.toString());
		}
		return super.asXMLElement(document, Path_element);
	}
}