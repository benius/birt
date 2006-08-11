/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.tests.model.regression;

import org.eclipse.birt.report.model.api.DesignFileException;
import org.eclipse.birt.report.model.api.LabelHandle;
import org.eclipse.birt.report.model.api.SharedStyleHandle;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.tests.model.BaseTestCase;

/**
 * Regression description:
 * </p>
 * Style name can't be changed to None in Property Editor after delete the style
 * </p>
 * Test description:
 * <p>
 * Delete a style, change the element style to None
 * </p>
 */

public class Regression_142137 extends BaseTestCase
{

	private String filename = "Regression_142137.xml"; //$NON-NLS-1$

	/**
	 * @throws DesignFileException
	 * @throws SemanticException
	 */
	public void test_142137( ) throws DesignFileException, SemanticException
	{
		openDesign( filename );
		SharedStyleHandle style = designHandle.findStyle( "s1" ); //$NON-NLS-1$
		style.drop( );
		assertEquals( 0, designHandle.getStyles( ).getCount( ) );

		LabelHandle label = (LabelHandle) designHandle.findElement( "label" ); //$NON-NLS-1$
		label.setStyleName( null );
		assertEquals( null, label.getStyle( ) );

	}
}
