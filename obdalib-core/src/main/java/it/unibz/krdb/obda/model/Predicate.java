package it.unibz.krdb.obda.model;

/*
 * #%L
 * ontop-obdalib-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.Serializable;

/**
* The Predicate class currently represents (1) first-order predicts, (2) function symbols, and
 * (3) logical operators (e.g. join, left join)
 *
 */
public interface Predicate extends Cloneable, Serializable {

	public enum COL_TYPE {
		OBJECT,
		BNODE,
		LITERAL,
		LITERAL_LANG,
		INTEGER,
		INT,
		NON_NEGATIVE_INTEGER,
		POSITIVE_INTEGER,
		NEGATIVE_INTEGER,
		NON_POSITIVE_INTEGER,
		UNSIGNED_INT,
		DECIMAL,
		DOUBLE,
		FLOAT,
		STRING,
		DATETIME,
		BOOLEAN,
		UNSUPPORTED,
		DATE,
		TIME,
		YEAR,
		LONG,
		NULL ;
    };

	/**
	 * Get the name of the predicate. In practice, the predicate name is
	 * constructed as a URI to indicate a unique resource.
	 * 
	 * @return the resource identifier (URI).
	 */
	public String getName();

	/**
	 * Get the number of elements of the predicate.
	 * 
	 * @return an integer number.
	 */
	public int getArity();

	/***
	 * Returns the typing of the component given by component. Types can be
	 * "Object" or "Literal", defined by the inner enumerator PRED_TYPE
	 * 
	 * @param column
	 */
	public COL_TYPE getType(int column);

	/**
	 * Duplicate the object by performing a deep cloning.
	 * 
	 * @return the copy of the object.
	 */
	public Predicate clone();

	boolean isClass();

	boolean isObjectProperty();

	boolean isDataProperty();
	
	boolean isDataPredicate();
	
	boolean isBooleanPredicate();
	
	boolean isAlgebraPredicate();
	
	boolean isArithmeticPredicate();
	
	boolean isDataTypePredicate();

	boolean isTriplePredicate();
}
