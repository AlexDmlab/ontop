package org.semanticweb.ontop.model.impl;

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



import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.semanticweb.ontop.model.BNode;
import org.semanticweb.ontop.model.CQIE;
import org.semanticweb.ontop.model.Constant;
import org.semanticweb.ontop.model.DatalogProgram;
import org.semanticweb.ontop.model.DatatypeFactory;
import org.semanticweb.ontop.model.Function;
import org.semanticweb.ontop.model.OBDADataFactory;
import org.semanticweb.ontop.model.OBDADataSource;
import org.semanticweb.ontop.model.OBDAModel;
import org.semanticweb.ontop.model.OBDAQuery;
import org.semanticweb.ontop.model.OBDARDBMappingAxiom;
import org.semanticweb.ontop.model.Predicate;
import org.semanticweb.ontop.model.Term;
import org.semanticweb.ontop.model.URIConstant;
import org.semanticweb.ontop.model.ValueConstant;
import org.semanticweb.ontop.model.Variable;
import org.semanticweb.ontop.model.Predicate.COL_TYPE;
import org.semanticweb.ontop.utils.IDGenerator;
import org.semanticweb.ontop.utils.JdbcTypeMapper;

public class OBDADataFactoryImpl implements OBDADataFactory {

	private static final long serialVersionUID = 1851116693137470887L;
	
	private static OBDADataFactory instance = null;
	private static ValueFactory irifactory = null;
	private DatatypeFactoryImpl datatypes = null;
	private final JdbcTypeMapper jdbcTypeMapper =  new JdbcTypeMapper();
	

	private static int counter = 0;
	
	protected OBDADataFactoryImpl() {
		// protected constructor prevents instantiation from other classes.
	}

	public static OBDADataFactory getInstance() {
		if (instance == null) {
			instance = new OBDADataFactoryImpl();
		}
		return instance;
	}
	
	public static ValueFactory getIRIFactory() {
		if (irifactory == null) {
			irifactory = new ValueFactoryImpl();
		}
		return irifactory;
	}
	
	@Override
	public DatatypeFactory getDatatypeFactory() {
		if (datatypes == null) {
			datatypes = new DatatypeFactoryImpl();
		}
		return datatypes;
	}

	
	@Override 
	public JdbcTypeMapper getJdbcTypeMapper() {
		return jdbcTypeMapper;
	}
	
	
		
	public OBDAModel getOBDAModel() {
		return new OBDAModelImpl();
	}

	@Deprecated
	public PredicateImpl getPredicate(String name, int arity) {
//		if (arity == 1) {
//			return new PredicateImpl(name, arity, new COL_TYPE[] { COL_TYPE.OBJECT });
//		} else {
			return new PredicateImpl(name, arity, null);
//		}
	}
	
	@Override
	public Predicate getPredicate(String uri, COL_TYPE[] types) {
		return new PredicateImpl(uri, types.length, types);
	}


	public Predicate getObjectPropertyPredicate(String name) {
		return new PredicateImpl(name, 2, new COL_TYPE[] { COL_TYPE.OBJECT, COL_TYPE.OBJECT });
	}

	public Predicate getDataPropertyPredicate(String name) {
		return new PredicateImpl(name, 2, new COL_TYPE[] { COL_TYPE.OBJECT, COL_TYPE.LITERAL }); 
	}
	public Predicate getDataPropertyPredicate(String name, COL_TYPE type) {
		return new PredicateImpl(name, 2, new COL_TYPE[] { COL_TYPE.OBJECT, type }); // COL_TYPE.LITERAL
	}

	public Predicate getClassPredicate(String name) {
		return new PredicateImpl(name, 1, new COL_TYPE[] { COL_TYPE.OBJECT });
	}
	
	@Override
	@Deprecated
	public URIConstant getConstantURI(String uriString) {
		return new URIConstantImpl(uriString);
	}
	
	@Override
	public ValueConstant getConstantLiteral(String value) {
		return new ValueConstantImpl(value, COL_TYPE.LITERAL);
	}

	@Override
	public ValueConstant getConstantLiteral(String value, COL_TYPE type) {
		return new ValueConstantImpl(value, type);
	}

	@Override
	public Function getTypedTerm(Term value, COL_TYPE type) {
		Predicate pred = getDatatypeFactory().getTypePredicate(type);
		if (pred == null)
			throw new RuntimeException("Unknown data type!");
		
		return getFunction(pred, value);
	}
	
	@Override
	public ValueConstant getConstantLiteral(String value, String language) {
		return new ValueConstantImpl(value, language.toLowerCase());
	}

	@Override
	public Function getTypedTerm(Term value, Term language) {
		Predicate pred = getDatatypeFactory().getTypePredicate(COL_TYPE.LITERAL_LANG);
		return getFunction(pred, value, language);
	}

	@Override
	public Function getTypedTerm(Term value, String language) {
		Term lang = getConstantLiteral(language.toLowerCase(), COL_TYPE.LITERAL);		
		Predicate pred = getDatatypeFactory().getTypePredicate(COL_TYPE.LITERAL_LANG);
		return getFunction(pred, value, lang);
	}
	
	@Override
	public ValueConstant getConstantFreshLiteral() {
		// TODO: a bit more elaborate name is needed to avoid conflicts
		return new ValueConstantImpl("f" + (counter++), COL_TYPE.LITERAL);
	}

	@Override
	public Variable getVariable(String name) {
		return new VariableImpl(name);
	}
	

	@Override
	public Variable getVariableNondistinguished() {
		return new AnonymousVariable();
	}

	@Override
	public Function getFunction(Predicate functor, Term... arguments) {
		return new FunctionalTermImpl(functor, arguments);
	}
	
	@Override
	public Function getFunction(Predicate functor, List<Term> arguments) {
		return new FunctionalTermImpl(functor, arguments);
	}

	@Override
	public OBDADataSource getDataSource(URI id) {
		return new DataSourceImpl(id);
	}

	@Override
	public CQIE getCQIE(Function head, Function... body) {
		return new CQIEImpl(head, body);
	}
	
	@Override
	public CQIE getCQIE(Function head, List<Function> body) {
		return new CQIEImpl(head, body);
	}
	
	@Override
	public DatalogProgram getDatalogProgram() {
		return new DatalogProgramImpl();
	}

	@Override
	public DatalogProgram getDatalogProgram(CQIE rule) {
		DatalogProgram p = new DatalogProgramImpl();
		p.appendRule(rule);
		return p;
	}

	@Override
	public DatalogProgram getDatalogProgram(Collection<CQIE> rules) {
		DatalogProgram p = new DatalogProgramImpl();
		p.appendRule(rules);
		return p;
	}

	@Override
	public RDBMSMappingAxiomImpl getRDBMSMappingAxiom(String id, OBDAQuery sourceQuery, OBDAQuery targetQuery) {
		return new RDBMSMappingAxiomImpl(id, sourceQuery, targetQuery);
	}

	@Override
	public SQLQueryImpl getSQLQuery(String query) {
		return new SQLQueryImpl(query);
	}

	@Override
	public OBDARDBMappingAxiom getRDBMSMappingAxiom(String id, String sql, OBDAQuery targetQuery) {
		return new RDBMSMappingAxiomImpl(id, new SQLQueryImpl(sql), targetQuery);
	}

	@Override
	public OBDARDBMappingAxiom getRDBMSMappingAxiom(String sql, OBDAQuery targetQuery) {
		String id = new String(IDGenerator.getNextUniqueID("MAPID-"));
		return getRDBMSMappingAxiom(id, sql, targetQuery);
	}

	
	
	
	
	

	
	@Override
	public Function getUriTemplate(Term... terms) {
		Predicate uriPred = new URITemplatePredicateImpl(terms.length);
		return getFunction(uriPred, terms);		
	}
	
	@Override
	public Function getUriTemplate(List<Term> terms) {
		Predicate uriPred = new URITemplatePredicateImpl(terms.size());
		return getFunction(uriPred, terms);		
	}

	@Override
	public Function getUriTemplateForDatatype(String type) {
		return getFunction(new URITemplatePredicateImpl(1), getConstantLiteral(type, COL_TYPE.OBJECT));
	}
	
	@Override
	public Function getBNodeTemplate(Term... terms) {
		Predicate pred = new BNodePredicateImpl(terms.length);
		return getFunction(pred, terms);
	}
	
	@Override
	public Function getBNodeTemplate(List<Term> terms) {
		Predicate pred = new BNodePredicateImpl(terms.size());
		return getFunction(pred, terms);
	}

	@Override
	public Function getFunctionEQ(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.EQ, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionGTE(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.GTE, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionGT(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.GT, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionLTE(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.LTE, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionLT(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.LT, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionNEQ(Term firstTerm, Term secondTerm) {
		return getFunction(OBDAVocabulary.NEQ, firstTerm, secondTerm);
	}

	@Override
	public Function getFunctionNOT(Term term) {
		return getFunction(OBDAVocabulary.NOT, term);
	}

	@Override
	public Function getFunctionAND(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.AND, term1, term2);
	}

//	@Override
//	public Function getANDFunction(List<Term> terms) {
//		if (terms.size() < 2) {
//			throw new IllegalArgumentException("AND requires at least 2 terms");
//		}
//		LinkedList<Term> auxTerms = new LinkedList<Term>();
//
//		if (terms.size() == 2) {
//			return getFunctionalTerm(OBDAVocabulary.AND, terms.get(0), terms.get(1));
//		}
//		Term nested = getFunctionalTerm(OBDAVocabulary.AND, terms.get(0), terms.get(1));
//		terms.remove(0);
//		terms.remove(0);
//		while (auxTerms.size() > 1) {
//			nested = getFunctionalTerm(OBDAVocabulary.AND, nested, terms.get(0));
//			terms.remove(0);
//		}
//		return getFunctionalTerm(OBDAVocabulary.AND, nested, terms.get(0));
//	}

	@Override
	public Function getFunctionOR(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.OR, term1, term2);
	}

	
//	@Override
//	public Function getORFunction(List<Term> terms) {
//		if (terms.size() < 2) {
//			throw new IllegalArgumentException("OR requires at least 2 terms");
//		}
//		LinkedList<Term> auxTerms = new LinkedList<Term>();
//
//		if (terms.size() == 2) {
//			return getFunctionalTerm(OBDAVocabulary.OR, terms.get(0), terms.get(1));
//		}
//		Term nested = getFunctionalTerm(OBDAVocabulary.OR, terms.get(0), terms.get(1));
//		terms.remove(0);
//		terms.remove(0);
//		while (auxTerms.size() > 1) {
//			nested = getFunctionalTerm(OBDAVocabulary.OR, nested, terms.get(0));
//			terms.remove(0);
//		}
//		return getFunctionalTerm(OBDAVocabulary.OR, nested, terms.get(0));
//	}

	@Override
	public Function getFunctionIsNull(Term term) {
		return getFunction(OBDAVocabulary.IS_NULL, term);
	}

	@Override
	public Function getFunctionIsNotNull(Term term) {
		return getFunction(OBDAVocabulary.IS_NOT_NULL, term);
	}


	@Override
	public Function getLANGMATCHESFunction(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.SPARQL_LANGMATCHES, term1, term2);
	}

	@Override
	public Function getFunctionLike(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.SPARQL_LIKE, term1, term2);
	}
	
	@Override
	public Function getFunctionRegex(Term term1, Term term2, Term term3) {
		return getFunction(OBDAVocabulary.SPARQL_REGEX, term1, term2, term3 );
	}

    @Override
    public Function getFunctionReplace(Term term1, Term term2, Term term3) {
        return getFunction(OBDAVocabulary.REPLACE, term1, term2, term3 );
    }
	
	@Override
	public Function getFunctionMinus(Term term1) {
		return getFunction(OBDAVocabulary.MINUS, term1);
	}

	@Override
	public Function getFunctionAdd(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.ADD, term1, term2);
	}

	@Override
	public Function getFunctionSubstract(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.SUBTRACT, term1, term2);
	}

	@Override
	public Function getFunctionMultiply(Term term1, Term term2) {
		return getFunction(OBDAVocabulary.MULTIPLY, term1, term2);
	}

    @Override
    public Function getFunctionConcat(Term term1, Term term2) {
        return getFunction(OBDAVocabulary.CONCAT, term1, term2);
    }

	@Override
	public Function getFunctionCast(Term term1, Term term2) {
		// TODO implement cast function
		return getFunction(OBDAVocabulary.QUEST_CAST, term1, term2);
	}
	
	@Override
	public OBDADataSource getJDBCDataSource(String jdbcurl, String username, 
			String password, String driverclass) {
		URI id = URI.create(UUID.randomUUID().toString());
		return getJDBCDataSource(id.toString(), jdbcurl, username, password, driverclass);
	}

	@Override
	public OBDADataSource getJDBCDataSource(String sourceuri, String jdbcurl, 
			String username, String password, String driverclass) {
		DataSourceImpl source = new DataSourceImpl(URI.create(sourceuri));
		source.setParameter(RDBMSourceParameterConstants.DATABASE_URL, jdbcurl);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD, password);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_USERNAME, username);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_DRIVER, driverclass);
		return source;
	}

	
	@Override
	public BNode getConstantBNode(String name) {
		return new BNodeConstantImpl(name);
	}

	@Override
	public Function getFunctionIsTrue(Term term) {
		return getFunction(OBDAVocabulary.IS_TRUE, term);
	}

	@Override
	public Function getSPARQLJoin(Term t1, Term t2) {
		return getFunction(OBDAVocabulary.SPARQL_JOIN, t1, t2);
	}

	@Override
	public Function getSPARQLLeftJoin(Term t1, Term t2) {
		return getFunction(OBDAVocabulary.SPARQL_LEFTJOIN, t1, t2);
	}

	@Override
	public ValueConstant getBooleanConstant(boolean value) {
		return value ? OBDAVocabulary.TRUE : OBDAVocabulary.FALSE;
	}

	@Override
	public Function getTripleAtom(Term subject, Term predicate, Term object) {
		return getFunction(OBDAVocabulary.QUEST_TRIPLE_PRED, subject, predicate, object);
	}

	
}
