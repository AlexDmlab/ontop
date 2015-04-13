package org.semanticweb.ontop.owlrefplatform.core.translator;

/*
 * #%L
 * ontop-reformulation-core
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


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.Vector;

import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.datatypes.XMLDatatypeUtil;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.algebra.FunctionCall;
import org.openrdf.query.algebra.AggregateOperator;
import org.openrdf.query.algebra.And;
import org.openrdf.query.algebra.Avg;
import org.openrdf.query.algebra.BinaryTupleOperator;
import org.openrdf.query.algebra.BinaryValueOperator;
import org.openrdf.query.algebra.Bound;
import org.openrdf.query.algebra.Compare;
import org.openrdf.query.algebra.Compare.CompareOp;
import org.openrdf.query.algebra.Count;
import org.openrdf.query.algebra.Datatype;
import org.openrdf.query.algebra.Distinct;
import org.openrdf.query.algebra.Extension;
import org.openrdf.query.algebra.ExtensionElem;
import org.openrdf.query.algebra.Filter;
import org.openrdf.query.algebra.Group;
import org.openrdf.query.algebra.IsBNode;
import org.openrdf.query.algebra.IsLiteral;
import org.openrdf.query.algebra.IsURI;
import org.openrdf.query.algebra.Join;
import org.openrdf.query.algebra.Lang;
import org.openrdf.query.algebra.LangMatches;
import org.openrdf.query.algebra.LeftJoin;
import org.openrdf.query.algebra.MathExpr;
import org.openrdf.query.algebra.MathExpr.MathOp;
import org.openrdf.query.algebra.Max;
import org.openrdf.query.algebra.Min;
import org.openrdf.query.algebra.Not;
import org.openrdf.query.algebra.Or;
import org.openrdf.query.algebra.Order;
import org.openrdf.query.algebra.OrderElem;
import org.openrdf.query.algebra.Projection;
import org.openrdf.query.algebra.ProjectionElem;
import org.openrdf.query.algebra.Reduced;
import org.openrdf.query.algebra.Regex;
import org.openrdf.query.algebra.SameTerm;
import org.openrdf.query.algebra.Slice;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.Str;
import org.openrdf.query.algebra.Sum;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.UnaryTupleOperator;
import org.openrdf.query.algebra.UnaryValueOperator;
import org.openrdf.query.algebra.Union;
import org.openrdf.query.algebra.ValueExpr;
import org.openrdf.query.algebra.Var;
import org.openrdf.query.parser.ParsedGraphQuery;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.ParsedTupleQuery;
import org.semanticweb.ontop.model.CQIE;
import org.semanticweb.ontop.model.Constant;
import org.semanticweb.ontop.model.DataTypePredicate;
import org.semanticweb.ontop.model.DatalogProgram;
import org.semanticweb.ontop.model.DatatypeFactory;
import org.semanticweb.ontop.model.Function;
import org.semanticweb.ontop.model.OBDADataFactory;
import org.semanticweb.ontop.model.Predicate;
import org.semanticweb.ontop.model.Term;
import org.semanticweb.ontop.model.ValueConstant;
import org.semanticweb.ontop.model.Variable;
import org.semanticweb.ontop.model.Predicate.COL_TYPE;
import org.semanticweb.ontop.model.impl.DataTypePredicateImpl;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.model.impl.OBDAVocabulary;
import org.semanticweb.ontop.owlrefplatform.core.abox.SemanticIndexURIMap;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.Substitution;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.SubstitutionUtilities;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.UriTemplateMatcher;
import org.semanticweb.ontop.model.impl.TermUtils;
import org.slf4j.LoggerFactory;

/***
 * Translate a SPARQL algebra expression into a Datalog program that has the
 * same semantics. We use the built-int predicates Join and Left join. The rules
 * in the program have always 1 or 2 operator atoms, plus (in)equality atoms
 * (due to filters).
 * 
 * This programs needs to be flattened by another procedure later.
 * 
 * @author mariano
 */
public class SparqlAlgebraToDatalogTranslator {
	
	private final OBDADataFactory ofac = OBDADataFactoryImpl.getInstance();
	
	private final DatatypeFactory dtfac = OBDADataFactoryImpl.getInstance().getDatatypeFactory();

	private final TermComparator comparator = new TermComparator();

	private UriTemplateMatcher uriTemplateMatcher;

	private SemanticIndexURIMap uriRef = null;  // used only in the Semantic Index mode
	
	public SparqlAlgebraToDatalogTranslator(UriTemplateMatcher templateMatcher) {
		uriTemplateMatcher = templateMatcher;
	}
	
	public void setTemplateMatcher(UriTemplateMatcher templateMatcher) {
		uriTemplateMatcher = templateMatcher;
	}
	
	public void setSemanticIndexUriRef(SemanticIndexURIMap uriRef) {
		this.uriRef = uriRef;
	}
	

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SparqlAlgebraToDatalogTranslator.class);

	public DatalogProgram translate(ParsedQuery pq, List<String> signature) {
		TupleExpr te = pq.getTupleExpr();

		log.debug("SPARQL algebra: \n{}", te);
		DatalogProgram result = ofac.getDatalogProgram();

		// Render the variable names in the signature into Variable object
		List<Variable> vars = new LinkedList<Variable>();
		for (String vs : signature) {
			vars.add(ofac.getVariable(vs));
		}
		int[] freshvarcount = { 1 };

		Function bodyAtom = translate(vars, te, result, OBDAVocabulary.QUEST_QUERY + "0", freshvarcount);
        //createRule(result, OBDAVocabulary.QUEST_QUERY, (Variable)vars, bodyAtom);
		return result;
	}
	
	/**
	 * Translate a given SPARQL query object to datalog program.
	 * 
	 *
	 *            The Query object.
	 * @return Datalog program that represents the construction of the SPARQL
	 *         query.
	 */
//	public DatalogProgram translate(Query arqQuery, List<String> signature) {
//
//		Op op = Algebra.compile(arqQuery);
//
//		log.debug("SPARQL algebra: \n{}", op);
//
//		DatalogProgram result = ofac.getDatalogProgram();
//
//		// Render the variable names in the signature into Variable object
//		List<Variable> vars = new LinkedList<Variable>();
//		for (String vs : signature) {
//			vars.add(ofac.getVariable(vs));
//		}
//
//		int[] freshvarcount = { 1 };
//
//		//translate(vars, op, result, 1, freshvarcount);
//		return result;
//	}

	private Function translate(List<Variable> vars, TupleExpr te,
			DatalogProgram pr, String newHeadName, int[] varcount) {
		if (te instanceof Slice) {

			// Add LIMIT and OFFSET modifiers, if any
			Slice slice = (Slice) te;
			return translate(vars, slice, pr, newHeadName, varcount);

		} else if (te instanceof Distinct) {

			// Add DISTINCT modifier, if any
			Distinct distinct = (Distinct) te;
			return translate(vars, distinct, pr, newHeadName, varcount);

		} else if (te instanceof Projection) {

			// Add PROJECTION modifier, if any
			Projection project = (Projection) te;
			return translate(vars, project, pr, newHeadName, varcount);

		} else if (te instanceof Order) {

			// Add ORDER BY modifier, if any
			Order order = (Order) te;
			return translate(vars, order, pr, newHeadName, varcount);
		
		} else if (te instanceof Group) { 
			Group gr = (Group) te;
			return translate(vars, gr, pr, newHeadName, varcount);
			
		} else if (te instanceof Filter) {
			Filter filter = (Filter) te;
			return translate(vars, filter, pr, newHeadName, varcount);

		} else if (te instanceof StatementPattern) {

			StatementPattern stmp = (StatementPattern) te;
			return translate(stmp);

		} else if (te instanceof Join) {
			Join join = (Join) te;
			return translate(vars, join, pr, newHeadName, varcount);

		} else if (te instanceof Union) {
			Union union = (Union) te;
			return translate(vars, union, pr, newHeadName, varcount);

		} else if (te instanceof LeftJoin) {
			LeftJoin join = (LeftJoin) te;
			return translate(vars, join, pr, newHeadName, varcount);
		
		} else if (te instanceof Reduced) {
			return translate(vars, ((Reduced) te).getArg(), pr, newHeadName, varcount);
		
		} else if (te instanceof Extension) { 
			Extension extend = (Extension) te;
			return translate(vars, extend, pr, newHeadName, varcount);
		
		} else {
			try {
				throw new QueryEvaluationException("Operation not supported: "
						+ te.toString());
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
        return null;
	}


//    /**
//     * EXTEND { (T_j AS V_j) } EXPR
//     *
//     * where the T_j are built from the variables X of EXPR,
//     *
//     * adds the following rule:
//     *
//     *   ans_i(X * T) :- ans_{i.0}(X)
//     *
//     * @param extend
//     * @param pr
//     * @param newHeadName
//     * @return
//     */
//
//    private Function translate(List<Variable> vars, Extension extend, DatalogProgram pr, String newHeadName, int[] varcount) {
//
//        Set<Variable> VarsSet = getVariables(extend.getArg());
//        List<Variable> VarsList = new LinkedList<Variable>();
//        VarsList.addAll(VarsSet);
//        Function subAtom = translate(VarsList, extend.getArg(), pr, newHeadName + "0", varcount);
//        Set<Variable> subVarSet = getVariables(subAtom);
//
//        int sz = subVarSet.size() + extend.getElements().size();
//        List<Term> varList = new ArrayList<>(sz);
//        varList.addAll(subVarSet);
//        List<Term> termList = new ArrayList<>(sz);
//        termList.addAll(varList);
//        TupleExpr subte = extend.getArg();
//        List<Term> atom1VarsList = new LinkedList<Term>();
//
//
//        for (ExtensionElem el: extend.getElements()) {
//            Variable var = ofac.getVariable(el.getName());
//            varList.add(var);
//
//            Term term = getExpression(el.getExpr());
//            termList.add(term);
//            /**
//             //			 * When there is an aggregate in the head,
//             //			 * the arity of the atom is reduced.
//             //			 *
//             //			 * This atom usually appears in parent rules, so
//             //			 * its arity must be fixed in these rules.
//             //			 */
//            Set<Variable> atom1VarsSet = getBindVariables(subte);
//            atom1VarsList.addAll(atom1VarsSet);
//            atom1VarsList.add(var);
//            Collections.sort(atom1VarsList, comparator);
//            List<Term> atom1Variables = new ArrayList<>(atom1VarsList);
//            if (el.getExpr() instanceof AggregateOperator) {
//			    pr = updateArity(subAtom.getFunctionSymbol(), atom1Variables, pr);
//			}
//
//        }
//        CQIE rule = createRule(pr, newHeadName, termList, subAtom);
//
//        Function newHeadAtom = ofac.getFunction(rule.getHead().getFunctionSymbol(), varList);
//        return newHeadAtom;
//    }

	private Function translate(List<Variable> vars, Extension extend,
			DatalogProgram pr, String newHeadName, int[] varcount) {
		TupleExpr subte = extend.getArg();
		List<ExtensionElem> elements = extend.getElements();
		Set<Variable> atom2VarsSet = null;
        CQIE newrule = null;

        /**
         * TODO: why are they outside the LOOP??
         * Will we really want to accumulate variables???
         */
		List<Term> atom2VarsList = new LinkedList<Term>();
		List<Term> atom1VarsList = new LinkedList<Term>();

        atom2VarsSet = getVariables(subte);

        atom2VarsList.addAll(atom2VarsSet);
        Collections.sort(atom2VarsList, comparator);
        Predicate rightAtomPred = ofac.getPredicate(newHeadName + "0",
                atom2VarsList.size());
        Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);

        /**
         * Translating the rest
         */
        List<Variable> vars1 = new LinkedList<Variable>();
        if (!atom2VarsList.isEmpty()){
            for (Term var1 : atom2VarsList)
                vars1.add((Variable) var1);
                translate(vars1, subte, pr, newHeadName + "0", varcount);
        } else{
                translate(vars, subte, pr, newHeadName, varcount);
        }

		for (ExtensionElem el: elements) {
			Variable var = null;

			String name = el.getName();
			ValueExpr vexp = el.getExpr();

			var = ofac.getVariable(name);
			Term term = getBooleanTerm(vexp);

			Set<Variable> atom1VarsSet = getBindVariables(subte);
			atom1VarsList.addAll(atom1VarsSet);

			atom1VarsList.add(var);
			Collections.sort(atom1VarsList, comparator);

            /**
             * Only variable names, no aggregation formula.
             */
            List<Term> atom1Variables = new ArrayList<>(atom1VarsList);

			int indexOfvar = atom1VarsList.indexOf(var);
			atom1VarsList.set(indexOfvar,term);
			Predicate leftAtomPred = ofac.getPredicate(newHeadName,
					atom1VarsList.size());
			Function head = ofac.getFunction(leftAtomPred, atom1VarsList);



			newrule = ofac.getCQIE(head, rightAtom);
            pr.appendRule(newrule);
			/**
			 * When there is an aggregate in the head,
			 * the arity of the atom is reduced.
			 *
			 * This atom usually appears in parent rules, so
			 * its arity must be fixed in these rules.
			 */
			if (vexp instanceof AggregateOperator) {
				pr = updateArity(leftAtomPred, atom1Variables, pr);
			}

		}

        return newrule.getHead();
	}

    /**
     * In some cases (like when aggregates are used), we cannot know in advance the final arity
     * of a given atom.
     * The strategy adopted here is to update this arity a posteriori if needed.
     *
     * For a given "ans_xy" function symbol, we look at all the already existing rules in the Datalog program
     * and replace each atom that use this function symbol.
     *
     * @param functionSymbol Read-only.
     * @param atomTerms terms to use to update atoms. Read-only.
     * @param programToUpdate Will be modified. Also returned.
     * @return the updated Datalog program
     */
	private DatalogProgram updateArity(Predicate functionSymbol, List<Term> atomTerms,
			DatalogProgram programToUpdate) {

        // New atom
		Function updatedAtom = ofac.getFunction(functionSymbol, atomTerms);

        /**
         * Looks at all the already existing rules (mostly parent rules)
         */
		for(CQIE rule : programToUpdate.getRules()) {

            /**
             * Looks at each body atom
             */
            List<Function> bodyAtoms = rule.getBody();
            for(int i = 0; i < bodyAtoms.size(); i++) {
                /**
                 * Replaces the atom if it uses the same answer function symbol
                 */
                Predicate localFunctionSymbol = bodyAtoms.get(i).getFunctionSymbol();
                if (localFunctionSymbol.equals(functionSymbol)) {
                    bodyAtoms.set(i, updatedAtom);
                }
            }
            /**
             * The rule is updated by side-effect (some people call it a bug).
             * Unfortunately, we cannot call rule.updateBody(bodyAtoms) ...
             * 
             * TODO: stop this practice.
             */
        }
		return programToUpdate;
	}

    /**
     * EXPR_1 UNION EXPR_2
     *
     * adds the following rules
     *
     * ans_i(X * NULL_1) :- ans_{i.0}(X_1)
     * ans_i(X * NULL_2) :- ans_{i.1}(X_2)
     *
     * Adding the UNION to the program, i.e., two rules Note, we need to
     * make null any head variables that do not appear in the body of the
     * components of the union, e.g,
     *
     * q(x,y,z) <- Union(R(x,y), R(x,z))
     *
     * results in
     *
     * q(x,y,null) :- ... R(x,y) ...
     * q(x,null,z) :- ... R(x,z) ...
     *
     * @param union
     * @param pr
     * @param newHeadName
     * @return
     */
    private Function translate(List<Variable> vars, Union union, DatalogProgram pr, String newHeadName, int[] varcount) {
        TupleExpr left = union.getLeftArg();
        TupleExpr right = union.getRightArg();
        Function leftAtom = translate(vars, left, pr, newHeadName + "0", varcount);
        Set<Variable> leftVars = getVariables(leftAtom);
        Function rightAtom = translate(vars, right, pr, newHeadName + "1", varcount);
        Set<Variable> rightVars = getVariables(rightAtom);
        List<Term> varList = getUnion(leftVars, rightVars);
        // left atom rule
        List<Term> leftTermList = new ArrayList<>(varList.size());
        for (Term t : varList) {
            Term lt = (leftVars.contains(t)) ? t : OBDAVocabulary.NULL;
            leftTermList.add(lt);
        }
        CQIE leftRule = createRule(pr, newHeadName, leftTermList, leftAtom);
        // right atom rule
        List<Term> rightTermList = new ArrayList<>(varList.size());
        for (Term t : varList) {
            Term lt = (rightVars.contains(t)) ? t : OBDAVocabulary.NULL;
            rightTermList.add(lt);
        }
        CQIE rightRule = createRule(pr, newHeadName, rightTermList, rightAtom);
        Function atom = ofac.getFunction(rightRule.getHead().getFunctionSymbol(), varList);
        return atom;
    }


//	private void translate(List<Variable> vars, Union union,
//			DatalogProgram pr, String newHeadName, int[] varcount) {
//		TupleExpr left = union.getLeftArg();
//		TupleExpr right = union.getRightArg();
//
//		/* Preparing the two atoms */
//
//		Set<Variable> atom1VarsSet = getVariables(left);
//		List<Term> atom1VarsList = new LinkedList<Term>();
//		atom1VarsList.addAll(atom1VarsSet);
//		Collections.sort(atom1VarsList, comparator);
//		Predicate leftAtomPred = ofac.getPredicate(newHeadName + "0",
//				atom1VarsList.size());
//		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);
//
//		Set<Variable> atom2VarsSet = getVariables(right);
//		List<Term> atom2VarsList = new LinkedList<Term>();
//		atom2VarsList.addAll(atom2VarsSet);
//		Collections.sort(atom2VarsList, comparator);
//		Predicate rightAtomPred = ofac.getPredicate(newHeadName + "1",
//				atom2VarsList.size());
//		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);
//
//		/* Preparing the head of the Union rules (2 rules) */
//		// Collections.sort(vars, comparator);
//		List<Term> headVars = new LinkedList<Term>();
//		for (Variable var : vars) {
//			headVars.add(var);
//		}
//		Predicate answerPred = ofac.getPredicate(newHeadName, vars.size());
//		Function head = ofac.getFunction(answerPred, headVars);
//
//		/*
//		 * Adding the UNION to the program, i.e., two rules Note, we need to
//		 * make null any head variables that do not appear in the body of the
//		 * uniones, e.g,
//		 *
//		 * q(x,y,z) <- Union(R(x,y), R(x,z))
//		 *
//		 * results in
//		 *
//		 * q(x,y,null) :- ... R(x,y) ... q(x,null,z) :- ... R(x,z) ...
//		 */
//
//		// finding out null
//		Set<Variable> nullVars = new HashSet<Variable>();
//		nullVars.addAll(vars);
//		nullVars.removeAll(atom1VarsSet); // the remaining variables do not
//											// appear in the body assigning
//											// null;
//		Substitution nullifier = SubstitutionUtilities.getNullifier(nullVars);
//		// making the rule
//		CQIE newrule1 = ofac.getCQIE(head, leftAtom);
//		pr.appendRule(SubstitutionUtilities.applySubstitution(newrule1, nullifier));
//
//		// finding out null
//		nullVars = new HashSet<Variable>();
//		nullVars.addAll(vars);
//		nullVars.removeAll(atom2VarsSet); // the remaining variables do not
//											// appear in the body assigning
//											// null;
//		nullifier = SubstitutionUtilities.getNullifier(nullVars);
//		// making the rule
//		CQIE newrule2 = ofac.getCQIE(head, rightAtom);
//		pr.appendRule(SubstitutionUtilities.applySubstitution(newrule2, nullifier));
//
//		/*
//		 * Translating the rest
//		 */
//		{
//			List<Variable> vars1 = new LinkedList<Variable>();
//			for (Term var : atom1VarsList)
//				vars1.add((Variable) var);
//			translate(vars1, left, pr, newHeadName + "0", varcount);
//		}
//		{
//			List<Variable> vars2 = new LinkedList<Variable>();
//			for (Term var : atom2VarsList)
//				vars2.add((Variable) var);
//			translate(vars2, right, pr, newHeadName + "1", varcount);
//		}
//
//	}

    private Function translate(List<Variable> vars, Join join, DatalogProgram pr, String newHeadName, int[] varcount) {
        TupleExpr left = join.getLeftArg();
		TupleExpr right = join.getRightArg();
        Function leftAtom = translate(vars, left, pr, newHeadName + "0", varcount);
        Function rightAtom = translate(vars, right, pr, newHeadName + "1", varcount);
        List<Term> varList = getUnionOfVariables(leftAtom, rightAtom);
        CQIE rule = createRule(pr, newHeadName, varList, leftAtom, rightAtom);
        return rule.getHead();
    }

//	private void translate(List<Variable> vars, Join join, DatalogProgram pr,
//			String newHeadName, int[] varcount) {
//		TupleExpr left = join.getLeftArg();
//		TupleExpr right = join.getRightArg();
//
//		/* Preparing the two atoms */
//
//		Set<Variable> atom1VarsSet = getVariables(left);
//		List<Term> atom1VarsList = new LinkedList<Term>();
//		atom1VarsList.addAll(atom1VarsSet);
//		Collections.sort(atom1VarsList, comparator);
//		Predicate leftAtomPred = ofac.getPredicate(newHeadName + "0",
//				atom1VarsList.size());
//		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);
//
//		Set<Variable> atom2VarsSet = getVariables(right);
//		List<Term> atom2VarsList = new LinkedList<Term>();
//		atom2VarsList.addAll(atom2VarsSet);
//		Collections.sort(atom2VarsList, comparator);
//		Predicate rightAtomPred = ofac.getPredicate(newHeadName + "1",
//				atom2VarsList.size());
//		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);
//		/* The join, this is no longer necessary, we will try to avoid explicit joins
//		as much as poosible, just use comma */
////		Predicate joinp = OBDAVocabulary.SPARQL_JOIN;
////		Function joinAtom = ofac.getFunction(joinp, leftAtom, rightAtom);
//
//		/* Preparing the head of the Join rule */
//		// Collections.sort(vars, comparator);
//		List<Term> headVars = new LinkedList<Term>();
//		for (Variable var : vars) {
//			headVars.add(var);
//		}
//		Predicate answerPred = ofac.getPredicate(newHeadName, vars.size());
//		Function head = ofac.getFunction(answerPred, headVars);
//
//		/*
//		 * Adding the join to the program
//		 */
//
//		CQIE newrule = ofac.getCQIE(head, leftAtom, rightAtom);
//		pr.appendRule(newrule);
//
//		/*
//		 * Translating the rest
//		 */
//		{
//			List<Variable> vars1 = new LinkedList<Variable>();
//			for (Term var : atom1VarsList)
//				vars1.add((Variable) var);
//			translate(vars1, left, pr, newHeadName + "0", varcount);
//		}
//		{
//			List<Variable> vars2 = new LinkedList<Variable>();
//			for (Term var : atom2VarsList)
//				vars2.add((Variable) var);
//			translate(vars2, right, pr, newHeadName + "1", varcount);
//		}
//	}

    private Function translate(List<Variable> vars,LeftJoin leftjoin, DatalogProgram pr, String newHeadName, int[] varcount) {
        TupleExpr left = leftjoin.getLeftArg();
        TupleExpr right = leftjoin.getRightArg();
        Function leftAtom = translate(vars, left, pr, newHeadName + "0", varcount);
        Function rightAtom = translate(vars, right, pr, newHeadName + "1", varcount);
        // the left join atom
        Function joinAtom = ofac.getSPARQLLeftJoin(leftAtom, rightAtom);
        // adding the conditions of the filter for the LeftJoin
        ValueExpr filter = leftjoin.getCondition();
        if (filter != null) {
            List<Term> joinTerms = joinAtom.getTerms();
            joinTerms.add((Function) getExpression(filter));
        }
        List<Term> varList = getUnionOfVariables(leftAtom, rightAtom);
        CQIE rule = createRule(pr, newHeadName, varList, joinAtom);
        return rule.getHead();
    }

//	private void translate(List<Variable> vars, LeftJoin join,
//			DatalogProgram pr, String newHeadName, int[] varcount) {
//		TupleExpr left = join.getLeftArg();
//		TupleExpr right = join.getRightArg();
//		ValueExpr filter = join.getCondition();
//
//		/* Preparing the two atoms */
//
//		Set<Variable> atom1VarsSet = getVariables(left);
//		List<Term> atom1VarsList = new LinkedList<Term>();
//		atom1VarsList.addAll(atom1VarsSet);
//		Collections.sort(atom1VarsList, comparator);
//		Predicate leftAtomPred = ofac.getPredicate(newHeadName + "0",
//				atom1VarsList.size());
//		Function leftAtom = ofac.getFunction(leftAtomPred, atom1VarsList);
//
//		Set<Variable> atom2VarsSet = getVariables(right);
//		List<Term> atom2VarsList = new LinkedList<Term>();
//		atom2VarsList.addAll(atom2VarsSet);
//		Collections.sort(atom2VarsList, comparator);
//		Predicate rightAtomPred = ofac.getPredicate(newHeadName + "1",
//				atom2VarsList.size());
//		Function rightAtom = ofac.getFunction(rightAtomPred, atom2VarsList);
//
//		/* The join */
//		Function joinAtom = ofac.getSPARQLLeftJoin(leftAtom, rightAtom);
//
//		/* adding the conditions of the filter for the LeftJoin */
//		if (filter != null) {
//
//			List<Term> joinTerms = joinAtom.getTerms();
//			joinTerms.add(((Function) getBooleanTerm(filter)));
////			for (Expr expr : filter.getList()) {
////				joinTerms.add(((Function) getBooleanTerm(expr)));
////			}
//
//		}
//
//		/* Preparing the head of the LeftJoin rule */
//		// Collections.sort(vars, comparator);
//		List<Term> headVars = new LinkedList<Term>();
//		for (Variable var : vars) {
//			headVars.add(var);
//		}
//		Predicate answerPred = ofac.getPredicate(newHeadName, vars.size());
//		Function head = ofac.getFunction(answerPred, headVars);
//
//		/*
//		 * Adding the join to the program
//		 */
//
//		List<Function> atoms = new LinkedList<Function>();
//		atoms.add(joinAtom);
//
//		CQIE newrule = ofac.getCQIE(head, atoms);
//		pr.appendRule(newrule);
//
//		/*
//		 * Translating the rest
//		 */
//		{
//			List<Variable> vars1 = new LinkedList<Variable>();
//			for (Term var : atom1VarsList)
//				vars1.add((Variable) var);
//			translate(vars1, left, pr, newHeadName+"0", varcount);
//		}
//		{
//			List<Variable> vars2 = new LinkedList<Variable>();
//			for (Term var : atom2VarsList)
//				vars2.add((Variable) var);
//			translate(vars2, right, pr, newHeadName+"1", varcount);
//		}
//	}

    /**
     * Projection SPARQL node.
     *
     * Creates one rule for the projection and adds it into the Datalog Program.
     *
     * In this rule, there is 1 head atom and 1 body atom.
     *
     * Basically, the head atom usually takes less arguments than the body atom
     *   --> that is the most common effect of the projection.
     *
     * Pursues by translating its child nodes (from the SPARQL tree).
     */
//	private Function translate(Projection project,
//			DatalogProgram pr, String newHeadName, int[] varcount) {
//
//		TupleExpr te = project.getArg();
//
//        // All variables --> for the body atom
//        List<Variable> allVariables = new ArrayList<>(getVariables(te));
//
//        // Projected variables --> for the head atom
//		List<Variable> projectedVariables = new ArrayList<>();
//		for (ProjectionElem var : project.getProjectionElemList().getElements()) {
//            // we assume here that the target name is "introduced" as one of the arguments of atom
//            // (this is normally done by an EXTEND inside the PROJECTION)
//            // first, we check whether this assumption can be made
//            if (!var.getSourceName().equals(var.getTargetName())) {
//                boolean found = false;
//                for (Term a : allVariables)
//                    if ((a instanceof Variable) && ((Variable)a).getName().equals(var.getSourceName())) {
//                        found = true;
//                        break;
//                    }
//                if (!found)
//                    throw new RuntimeException("Projection target of " + var + " not found in " + project.getArg());
//            }
//			projectedVariables.add(ofac.getVariable(var.getSourceName()));
//		}
//
//
//
//        /**
//         * Head: considers only the projected variables.
//         */
//		Predicate predicate = ofac.getPredicate(newHeadName,
//				projectedVariables.size());
//		Function headAtom = ofac.getFunction(predicate, new ArrayList<Term>(projectedVariables));
//
//        /**
//         * Body atom (just one).
//         * In many cases, the body atom takes more arguments than the head one.
//         *
//         * For this body atom, we don't know yet the right arity because
//         * we don't know if some aggregates are used or not.
//         *
//         * For the moment, we do like if there were no aggregate so all the variables are arguments
//         * of the atom.
//         * If latter, we find some aggregates, the atom arity will be updated.
//         */
//        List<Term> bodyAtomTerms = new ArrayList<Term>(allVariables);
//
//        Predicate bodyAtomFunctionSymbol = ofac.getPredicate(newHeadName+"0", allVariables.size());
//        Function bodyAtom = ofac.getFunction(bodyAtomFunctionSymbol, bodyAtomTerms);
//
//        translate(allVariables, te, pr, newHeadName + "0", varcount);
//
//        CQIE newRule = ofac.getCQIE(headAtom, bodyAtom);
//		pr.appendRule(newRule);
//
//		/**
//         * Continue the nested tree
//         */
//		return newRule.getHead();
//	}

    /**
     * PROJECT { V_j } EXPR
     *
     * adds the following rule
     *
     * ans_i(V) :- ans_{i.0}(X)
     *
     * @param project
     * @param pr
     * @param newHeadName
     * @return
     */
    private Function translate(List<Variable> vars, Projection project, DatalogProgram pr, String newHeadName, int[] varcount) {
        Function atom = translate(vars, project.getArg(), pr, newHeadName + "0", varcount);
        List<ProjectionElem> projectionElements = project.getProjectionElemList().getElements();
        List<Term> varList = new ArrayList<>(projectionElements.size());
        for (ProjectionElem var : projectionElements) {
        // we assume here that the target name is "introduced" as one of the arguments of atom
        // (this is normally done by an EXTEND inside the PROJECTION)
        // first, we check whether this assumption can be made
            if (!var.getSourceName().equals(var.getTargetName())) {
                boolean found = false;
                for (Term a : atom.getTerms())
                    if ((a instanceof Variable) && ((Variable)a).getName().equals(var.getSourceName())) {
                        found = true;
                        break;
                    }
                if (!found)
                    throw new RuntimeException("Projection target of " + var + " not found in " + project.getArg());
            }
            varList.add(ofac.getVariable(var.getTargetName()));
        }
        CQIE rule = createRule(pr, newHeadName, varList, atom);
        return rule.getHead();
    }

	private Function translate(List<Variable> vars, Slice slice,
			DatalogProgram pr, String newHeadName, int[] varcount) {
		TupleExpr te;
		pr.getQueryModifiers().setOffset(slice.getOffset());
		pr.getQueryModifiers().setLimit(slice.getLimit());
		te = slice.getArg(); // narrow down the query
		return translate(vars, te, pr, newHeadName, varcount);
	}

	private Function translate(List<Variable> vars, Distinct distinct,
			DatalogProgram pr, String newHeadName, int[] varcount) {
		TupleExpr te;
		pr.getQueryModifiers().setDistinct();
		te = distinct.getArg(); // narrow down the query
		return translate(vars, te, pr, newHeadName, varcount);
	}

	private Function translate(List<Variable> vars, Order order,
			DatalogProgram pr, String newHeadName, int[] varcount) {
		TupleExpr te;
		for (OrderElem c : order.getElements()) {
			
			ValueExpr expression = c.getExpr();
			if (!(expression instanceof Var)) {
				throw new IllegalArgumentException("Error translating ORDER BY. The current implementation can only sort by variables, this query has a more complex expression. Offending expression: '"+expression+"'");
			}
			Var v = (Var) expression;
			Variable var = ofac.getVariable(v.getName());
			int direction = 0;
			if (c.isAscending()) direction = 1;
            else direction = 2;
			pr.getQueryModifiers().addOrderCondition(var, direction);
		}
		te = order.getArg(); // narrow down the query
		return translate(vars, te, pr, newHeadName, varcount);
	}
	
	private Function translate(List<Variable> vars, Group group,
			DatalogProgram pr, String newHeadName, int[] varcount) {
        Function atom=translate(vars, group.getArg(), pr, newHeadName + "0", varcount);
		TupleExpr te;
		te = group.getArg(); // narrow down the query

		Set <String> bindings = group.getGroupBindingNames();
        CQIE cq = null;
//		List <GroupElem> gel = group.getGroupElements();
//		
//		for (GroupElem el: gel) {
//			AggregateOperator op = el.getOperator();
//			String name = el.getName();
//			int ii = 0;
//		}
		
		//Set<Variable> remainingVars= getVariables(te);
		
		//Construction the aggregate Atom
		if (!bindings.isEmpty()) {
            String nextVar = bindings.iterator().next();
            Variable groupvar = (Variable) ofac.getVariable(nextVar);
            Function aggregateAtom = ofac.getFunction(OBDAVocabulary.SPARQL_GROUP, groupvar);

            //Construction the head of the new rule
            Predicate predicate = ofac.getPredicate(newHeadName, vars.size());
            List<Term> termVars = new LinkedList<Term>();
            termVars.addAll(vars);
            Function head = ofac.getFunction(predicate, termVars);

            //Construction the body of the new rule that encode the rest of the tree
            Predicate pbody;
            Function bodyAtom;
            pbody = ofac.getPredicate(newHeadName + "0", vars.size());
            bodyAtom = ofac.getFunction(pbody, termVars);

            //Constructing the list
            LinkedList<Function> body = new LinkedList<Function>();
            body.add(bodyAtom);
            body.add(aggregateAtom);

            //Constructing the rule itself
//            CQIE rule = createRule(pr, newHeadName, termVars, atom);
             cq = ofac.getCQIE(head, body);
            pr.appendRule(cq);


        }

			/*
		Set <String> bindings = group.getGroupBindingNames();
		for (String b : bindings) {
			Variable var = ofac.getVariable(b);
			pr.getQueryModifiers().addGroupCondition(var);
		}
		te = group.getArg(); // narrow down the query
		translate(vars, te, pr, i, varcount);
			 */

			//iterating
//			return translate(vars, te, pr, newHeadName+"0", varcount);
//		}else{
//			return translate(vars, te, pr, newHeadName, varcount);
//		}

        return cq.getHead();
	}

    private Function translate(List<Variable> vars, Filter filter, DatalogProgram pr,
			String newHeadName, int varcount[]) {
        Function atom=translate(vars, filter.getArg(), pr, newHeadName + "0", varcount);
        Set<Variable> atomVars = getVariables(atom);
        ValueExpr condition = filter.getCondition();
        Function filterAtom;
        if (condition instanceof Var)
            filterAtom = ofac.getFunctionIsTrue(getOntopTerm((Var) condition));
        else
            filterAtom = (Function) getExpression(condition);
        Set<Variable> filterVars = new HashSet<>();
        TermUtils.addReferencedVariablesTo(filterVars, filterAtom);
        List<Term> var = getUnion(atomVars, filterVars);
        CQIE rule = createRule(pr, newHeadName, var, atom, filterAtom);
        return rule.getHead();
    }

//	public void translate(List<Variable> var, Filter filter, DatalogProgram pr,
//			String newHeadName, int varcount[]) {
//		ValueExpr condition = filter.getCondition();
//		List<Function> filterAtoms = new LinkedList<Function>();
//		Set<Variable> filteredVariables = new LinkedHashSet<Variable>();
//
//			Function a = null;
//			if (condition instanceof Var) {
//				a = ofac.getFunctionIsTrue(getVariableTerm((Var) condition));
//			} else {
//				a = (Function) getBooleanTerm(condition);
//			}
//			if (a != null) {
//				Function filterAtom = ofac.getFunction(a.getFunctionSymbol(),
//						a.getTerms());
//				filterAtoms.add(filterAtom);
//				filteredVariables.addAll(filterAtom.getReferencedVariables());
//			}
//
//		Predicate predicate = ofac.getPredicate(newHeadName, var.size());
//		List<Term> vars = new LinkedList<Term>();
//		vars.addAll(var);
//		Function head = ofac.getFunction(predicate, vars);
//
//		Predicate pbody;
//		Function bodyAtom;
//
//		List<Term> innerProjection = new LinkedList<Term>();
//		innerProjection.addAll(filteredVariables);
//		Collections.sort(innerProjection, comparator);
//
//		/***
//		 * This is necessary because some filters might apply to variables that
//		 * have not been projected yet, for example:
//		 * <p>
//		 * (filter (= ?x 99) <br>
//		 * <t> (bgp (triple <http://example/x> <http://example/p> ?x)))
//		 * <p>
//		 * in this cases we must project at least the filtered variables from
//		 * the nested expressions, otherwise we endup with free variables.
//		 *
//		 */
//
//		// TODO here we might be missing the case where there is a filter
//		// on a variable that has not been projected out of the inner
//		// expressions
//		if (vars.size() == 0 && filteredVariables.size() > 0) {
//			pbody = ofac.getPredicate(newHeadName + "0", innerProjection.size());
//			bodyAtom = ofac.getFunction(pbody, innerProjection);
//		} else {
//			pbody = ofac.getPredicate(newHeadName + "0", vars.size());
//			bodyAtom = ofac.getFunction(pbody, vars);
//		}
//
//		LinkedList<Function> body = new LinkedList<Function>();
//
//		TupleExpr sub = filter.getArg();
//		if (sub instanceof Extension) { // The filter is HAVING condition
//			List <Term> havingTerms = new LinkedList<Term> ();
//			havingTerms.addAll(filterAtoms);
//			Function havingAtom = ofac.getFunction(OBDAVocabulary.SPARQL_HAVING,havingTerms );
//			body.add(bodyAtom);
//			body.add(havingAtom);
//		} else {
//			body.add(bodyAtom);
//			body.addAll(filterAtoms);
//		}
//
//		CQIE cq = ofac.getCQIE(head, body);
//		pr.appendRule(cq);
//
//
//
//		if (vars.size() == 0 && filteredVariables.size() > 0) {
//			List<Variable> newvars = new LinkedList<Variable>();
//			for (Term l : innerProjection)
//				newvars.add((Variable) l);
//			translate(newvars, sub, pr, newHeadName + "0", varcount);
//		} else
//			translate(var, sub, pr, newHeadName + "0", varcount);
//
//	}

    /***
     * This translates a single triple.
     *
     * @param triple
     * @return
     */
    private Function translate(StatementPattern triple) {
        Var pred = triple.getPredicateVar();
        Value p = pred.getValue();
        if (!(p instanceof URI || (p == null))) {
// if predicate is a variable or literal
            throw new RuntimeException("Unsupported query syntax");
        }
        Var subj = triple.getSubjectVar();
        Var obj = triple.getObjectVar();
// Subject node
        Term sTerm = getOntopTerm(subj);
        if ((p != null) && p.toString().equals(RDF.TYPE.stringValue())) {
            Value o = obj.getValue();
// Object node
            if (o == null) {
                Function rdfTypeConstant = ofac.getUriTemplate(ofac.getConstantLiteral(OBDAVocabulary.RDF_TYPE));
                return ofac.getTripleAtom(sTerm, rdfTypeConstant, ofac.getVariable(obj.getName()));
            }
            else if (o instanceof URI) {
                URI objectUri = (URI)o;
                Predicate.COL_TYPE type = dtfac.getDataType((URI)objectUri);
                if (type != null) {
                    Predicate predicate = dtfac.getTypePredicate(type);
                    return ofac.getFunction(predicate, sTerm);
                }
                else {
                    COL_TYPE subjectType = null; // are never changed
                    Predicate predicate = ofac.getPredicate(objectUri.stringValue(), new COL_TYPE[] { subjectType });
                    return ofac.getFunction(predicate, sTerm);
                }
            }
            else
                throw new RuntimeException("Unsupported query syntax");
        }
        else {
// The predicate is NOT rdf:type
            Term oTerm = getOntopTerm(obj);
            if (p != null) {
                COL_TYPE subjectType = null; // are never changed
                COL_TYPE objectType = null;
                Predicate predicate = ofac.getPredicate(p.stringValue(), new COL_TYPE[] { subjectType, objectType });
                return ofac.getFunction(predicate, sTerm, oTerm);
            }
            else
                return ofac.getTripleAtom(sTerm, ofac.getVariable(pred.getName()), oTerm);
        }
    }

//	/***
//	 * This translates a single triple. In most cases it will generate one
//	 * single atom, however, if URI's are present, it will generate also
//	 * equality atoms.
//	 *
//	 * @param triple
//	 * @return
//	 */
//	public void translate(List<Variable> vars, StatementPattern triple,
//			DatalogProgram pr, String newHeadName, int[] varcount) {
//
//		Var obj = triple.getObjectVar();
//		Var pred = triple.getPredicateVar();
//		Var subj = triple.getSubjectVar();
//
//		Value o = obj.getValue();
//		Value p = pred.getValue();
//		Value s = subj.getValue();
//
//		if (!(p instanceof URIImpl || (p == null))) {
//			// if predicate is a variable or literal
//			throw new RuntimeException("Unsupported query syntax");
//		}
//
//		LinkedList<Function> result = new LinkedList<Function>();
//
//		// Instantiate the subject and object URI
//		URI objectUri = null;
//
//		// Instantiate the subject and object data type
//		COL_TYPE subjectType = null;
//		COL_TYPE objectType = null;
//
//		// / Instantiate the atom components: predicate and terms.
//		Predicate predicate = null;
//		Vector<Term> terms = new Vector<Term>();
//
//		if (p instanceof URIImpl && p.toString().equals(RDF.TYPE.stringValue())) {
//			// Subject node
//
//			terms.add(getOntopTerm(subj, s));
//
//			// Object node
//			if (o == null) {
//				predicate = OBDAVocabulary.QUEST_TRIPLE_PRED;
//
//				Function rdfTypeConstant = ofac.getUriTemplate(ofac.getConstantLiteral(OBDAVocabulary.RDF_TYPE));
//				terms.add(rdfTypeConstant);
//				terms.add(ofac.getVariable(obj.getName()));
//			}
//			else if (o instanceof LiteralImpl) {
//				throw new RuntimeException("Unsupported query syntax");
//			}
//			else if (o instanceof URIImpl) {
//				objectUri = (URI)o;
//			}
//
//			// Construct the predicate
//			if (objectUri == null) {
//				// NO OP, already assigned
//			}
//			else {
//				Predicate.COL_TYPE type = dtfac.getDataType(objectUri);
//				if (type != null) {
//					predicate = dtfac.getTypePredicate(type);
//				}
//	            else {
//					predicate = ofac.getPredicate(objectUri.stringValue(), new COL_TYPE[] { subjectType });
//				}
//			}
//		}
//		else {
//			/*
//			 * The predicate is NOT rdf:type
//			 */
//
//			terms.add(getOntopTerm(subj, s));
//
//			terms.add(getOntopTerm(obj,o));
//
//			// Construct the predicate
//
//			if (p instanceof URIImpl) {
//				String predicateUri = p.stringValue();
//				predicate = ofac.getPredicate(predicateUri, new COL_TYPE[] { subjectType, objectType });
//			}
//			else if (p == null) {
//				predicate = OBDAVocabulary.QUEST_TRIPLE_PRED;
//				terms.add(1, ofac.getVariable(pred.getName()));
//			}
//		}
//		// Construct the atom
//		Function atom = ofac.getFunction(predicate, terms);
//		result.addFirst(atom);
//
//		// Collections.sort(vars, comparator);
//		List<Term> newvars = new LinkedList<Term>();
//		for (Variable var : vars) {
//			newvars.add(var);
//		}
//
//		Predicate answerPred = ofac.getPredicate(newHeadName, vars.size());
//		Function head = ofac.getFunction(answerPred, newvars);
//
//		CQIE newrule = ofac.getCQIE(head, result);
//		pr.appendRule(newrule);
//	}
	
//	private Term getOntopTerm(Var subj, Value s) {
//		Term result = null;
//		if (s == null) {
//			result = ofac.getVariable(subj.getName());
//		} else if (s instanceof LiteralImpl) {
//			LiteralImpl object = (LiteralImpl) s;
//			COL_TYPE objectType = getDataType(object);
//			ValueConstant constant = getConstant(object);
//
//			// v1.7: We extend the syntax such that the data type of a
//			// constant
//			// is defined using a functional symbol.
//			Function dataTypeFunction = null;
//			if (objectType == COL_TYPE.LITERAL) {
//				// If the object has type LITERAL, check any language
//				// tag!
//				String lang = object.getLanguage();
//				if (lang != null && !lang.equals("")) {
//					result = ofac.getTypedTerm(constant, lang.toLowerCase());
//				}
//				else {
//					result =  ofac.getTypedTerm(constant, COL_TYPE.LITERAL);
//				}
//			}
//			else {
//				// For other supported data-types
//				dataTypeFunction = ofac.getTypedTerm(constant, objectType);
//				result= dataTypeFunction;
//			}
//		}
//		else if (s instanceof URIImpl) {
//			URIImpl subject = (URIImpl) s;
//			COL_TYPE subjectType = COL_TYPE.OBJECT;
//
//			String subject_URI = subject.stringValue();
//			subject_URI = decodeURIEscapeCodes(subject_URI);
//
//
//			if (uriRef != null) {
//				/* if in the Semantic Index mode */
//				int id = uriRef.getId(s.stringValue());
//
//				result = ofac.getUriTemplate(ofac.getConstantLiteral(String.valueOf(id), COL_TYPE.INTEGER));
//			} else {
//				result = uriTemplateMatcher.generateURIFunction(subject_URI);
//			}
//		}
//
//		return result;
//	}

    private Term getOntopTerm(Var subj) {
        Value s = subj.getValue();
        Term result = null;
        if (s == null) {
            result = ofac.getVariable(subj.getName());
        }
        else if (s instanceof LiteralImpl) {
            LiteralImpl object = (LiteralImpl) s;
            URI type = object.getDatatype();
            String value = object.getLabel();
            // Validating that the value is correct (lexically) with respect to the
            // specified datatype
            if (type != null) {
                boolean valid = XMLDatatypeUtil.isValidValue(value, type);
                if (!valid)
                    throw new RuntimeException("Invalid lexical form for datatype. Found: " + value);
            }
            COL_TYPE objectType;
            if (type == null)
                objectType = COL_TYPE.LITERAL;
            else {
                objectType = dtfac.getDataType((URI)type);
                if (objectType == null)
                    throw new RuntimeException("Unsupported datatype: " + type.stringValue());
            }
            // special case for decimal
            if ((objectType == COL_TYPE.DECIMAL) && !value.contains(".")) {
            // put the type as integer (decimal without fractions)
                objectType = COL_TYPE.INTEGER;
            }
            ValueConstant constant = ofac.getConstantLiteral(value, objectType);
            // v1.7: We extend the syntax such that the data type of a
            // constant is defined using a functional symbol.
            if (objectType == COL_TYPE.LITERAL) {
            // if the object has type LITERAL, check any language tag!
                String lang = object.getLanguage();
                if (lang != null && !lang.equals("")) {
                    result = ofac.getTypedTerm(constant, lang.toLowerCase());
                }
                else {
                    result = ofac.getTypedTerm(constant, objectType);
                }
            }
            else {
                result = ofac.getTypedTerm(constant, objectType);
            }
        }
        else if (s instanceof URI) {
            if (uriRef != null) {
                // if in the Semantic Index mode
                int id = uriRef.getId(s.stringValue());
                result = ofac.getUriTemplate(ofac.getConstantLiteral(String.valueOf(id), COL_TYPE.INTEGER));
            }
            else {
                String subject_URI = decodeURIEscapeCodes(s.stringValue());
                result = uriTemplateMatcher.generateURIFunction(subject_URI);
            }
        }
        return result;
    }
	
	/***
	 * Given a string representing a URI, this method will return a new String in which all percent encoded characters (e.g., %20) will
	 * be restored to their original characters (e.g., ' '). This is necessary to transform some URIs into the original dtabase values.
	 * @param encodedURI
	 * @return
	 */
	private String decodeURIEscapeCodes(String encodedURI) {
		int length = encodedURI.length();
		StringBuilder strBuilder = new StringBuilder(length+20);
		
		char[] codeBuffer = new char[2];
		
		for (int ci = 0; ci < length; ci++) {
			char c = encodedURI.charAt(ci);

			if (c != '%') {
				// base case, the character is a normal character, just
				// append
				strBuilder.append(c);
				continue;
			}

			/*
			 * found a escape, processing the code and replacing it by
			 * the original value that should be found on the DB. This
			 * should not be used all the time, only when working in
			 * virtual mode... we need to fix this with a FLAG.
			 */

			// First we get the 2 chars next to %
			codeBuffer[0] = encodedURI.charAt(ci + 1);
			codeBuffer[1] = encodedURI.charAt(ci + 2);

			// now we check if they match any of our escape wodes, if
			// they do the char to be inserted is put in codeBuffer
			// otherwise
			String code = String.copyValueOf(codeBuffer);
			if (code.equals("%20")) {
				strBuilder.append(' ');
			} else if (code.equals("%21")) {
				strBuilder.append('!');
			} else if (code.equals("%40")) {
				strBuilder.append('@');
			} else if (code.equals("%23")) {
				strBuilder.append('#');
			} else if (code.equals("%24")) {
				strBuilder.append('$');
			} else if (code.equals("%26")) {
				strBuilder.append('&');
			} else if (code.equals("%42")) {
				strBuilder.append('*');
			} else if (code.equals("%28")) {
				strBuilder.append('(');
			} else if (code.equals("%29")) {
				strBuilder.append(')');
			} else if (code.equals("%5B")) {
				strBuilder.append('[');
			} else if (code.equals("%5C")) {
				strBuilder.append(']');
			} else if (code.equals("%2C")) {
				strBuilder.append(',');
			} else if (code.equals("%3B")) {
				strBuilder.append(';');
			} else if (code.equals("%3A")) {
				strBuilder.append(':');
			} else if (code.equals("%3F")) {
				strBuilder.append('?');
			} else if (code.equals("%3D")) {
				strBuilder.append('=');
			} else if (code.equals("%2B")) {
				strBuilder.append('+');
			} else if (code.equals("%22")) {
				strBuilder.append('\'');
			} else if (code.equals("%2F")) {
				strBuilder.append('/');
			} else {
				// This was not an escape code, so we just append the
				// characters and continue;
				strBuilder.append(codeBuffer);
			}
			ci += 2;

		}
		return strBuilder.toString();

	}

    private Term getExpression(ValueExpr expr) {
        if (expr instanceof Var) {
            return getOntopTerm((Var) expr);
        }
        else if (expr instanceof org.openrdf.query.algebra.ValueConstant) {
            return getConstantExpression(((org.openrdf.query.algebra.ValueConstant) expr).getValue());
        }
        else if (expr instanceof UnaryValueOperator) {
            return getUnaryExpression((UnaryValueOperator) expr);
        }
        else if (expr instanceof BinaryValueOperator) {
            return getBinaryExpression((BinaryValueOperator) expr);
        }
        else if (expr instanceof Bound) {
            return ofac.getFunctionIsNotNull(getOntopTerm(((Bound) expr).getArg()));
        }
        else if (expr instanceof FunctionCall) {
            return getFunctionCallTerm((FunctionCall)expr);
        }
        throw new RuntimeException("The expression " + expr + " is not supported yet!");
    }


    private Term getConcat(List<ValueExpr> values) {
        Iterator<ValueExpr> iterator = values.iterator();
        ValueExpr first = iterator.next();
        Term topConcat = getExpression(first);
        if (!iterator.hasNext())
            throw new UnsupportedOperationException("Wrong number of arguments (found " + values.size() +
                    ", at least 1) of SQL function CONCAT");
        while (iterator.hasNext()) {
            ValueExpr second = iterator.next();
            Term second_string = getExpression(second);
            topConcat = ofac.getFunctionConcat(topConcat, second_string);
        }
        return topConcat;
    }
    private Term getReplace(List<ValueExpr> expressions) {
        if (expressions.size() == 2 || expressions.size() == 3) {
// first parameter is a function expression
            ValueExpr first = expressions.get(0);
            Term t1 = getExpression(first);
// second parameter is a string
            ValueExpr second = expressions.get(1);
            Term out_string = getExpression(second);
/*
* Term t3 is optional: no string means delete occurrences of second param
*/
            Term in_string;
            if (expressions.size() == 3) {
                ValueExpr third = expressions.get(2);
                in_string = getExpression(third);
            }
            else {
                in_string = ofac.getConstantLiteral("");
            }
            return ofac.getFunctionReplace(t1, out_string, in_string);
        }
        else
            throw new UnsupportedOperationException("Wrong number of arguments (found " + expressions.size() + ", only 2 or 3 supported) to sql function REPLACE");
    }

    /** Return the Functions supported at the moment only
     * concat and replace
     * @param expr
     * @return
     */
    private Term getFunctionCallTerm(FunctionCall expr) {
        switch(expr.getURI()){
            case "http://www.w3.org/2005/xpath-functions#concat":
                return getConcat(expr.getArgs());
            case "http://www.w3.org/2005/xpath-functions#replace":
                return getReplace(expr.getArgs());
            default:
                throw new RuntimeException("The builtin function " + expr.getURI() + " is not supported yet!");
        }
    }
    private Term getConstantExpression(Value v) {
        if (v instanceof LiteralImpl) {
            LiteralImpl lit = (LiteralImpl)v;
            URI type = lit.getDatatype();
            COL_TYPE tp;
            if (type == null) {
                tp = COL_TYPE.LITERAL;
            }
            else {
                tp = dtfac.getDataType((URI)type);
                if (tp == null) {
                    return ofac.getUriTemplateForDatatype(type.stringValue());
                }
            }
            String constantString;
            switch (tp) {
                case INTEGER:
                case NEGATIVE_INTEGER:
                case NON_POSITIVE_INTEGER:
                case POSITIVE_INTEGER:
                case NON_NEGATIVE_INTEGER:
                    constantString = lit.integerValue().toString();
                    break;
                case LONG:
                    constantString = lit.longValue() + "";
                    break;
                case DECIMAL:
                    constantString = lit.decimalValue().toString();
                    break;
                case FLOAT:
                    constantString = lit.floatValue() + "";
                    break;
                case DOUBLE:
                    constantString = lit.doubleValue() + "";
                    break;
                case INT:
                case UNSIGNED_INT:
                    constantString = lit.intValue() + "";
                    break;
             //   case DATETIME_STAMP:
                case DATETIME:
                case YEAR:
                case DATE:
                case TIME:
                    constantString = lit.calendarValue().toString();
                    break;
                case BOOLEAN:
                    constantString = lit.booleanValue() + "";
                    break;
                case STRING:
                case LITERAL:
                    constantString = lit.stringValue() + "";
                    break;
                default:
                    throw new RuntimeException("Undefined datatype: " + tp);
            }
            ValueConstant constant = ofac.getConstantLiteral(constantString, tp);
            return ofac.getTypedTerm(constant, tp);
        }
        else if (v instanceof URI) {
            Function constantFunction = uriTemplateMatcher.generateURIFunction(v.stringValue());
            if (constantFunction.getArity() == 1)
                constantFunction = ofac.getUriTemplateForDatatype(v.stringValue());
            return constantFunction;
        }
        throw new RuntimeException("The value " + v + " is not supported yet!");
    }
    private Term getUnaryExpression(UnaryValueOperator expr) {
        Term term = getExpression(expr.getArg());
        if (expr instanceof Not) {
            return ofac.getFunctionNOT(term);
        }
        else if (expr instanceof IsLiteral) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_IS_LITERAL, term);
        }
        else if (expr instanceof IsURI) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_IS_URI, term);
        }
        else if (expr instanceof Str) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_STR, term);
        }
        else if (expr instanceof Datatype) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_DATATYPE, term);
        }
        else if (expr instanceof IsBNode) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_IS_BLANK, term);
        }
        else if (expr instanceof Lang) {
            ValueExpr arg = expr.getArg();
            if (arg instanceof Var)
                return ofac.getFunction(OBDAVocabulary.SPARQL_LANG, term);
            else
                throw new RuntimeException("A variable or a value is expected in " + expr);
        }
        else if (expr instanceof Count) {
            if (expr.getArg() != null) {
                Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, getBooleanTerm( expr.getArg()));
                //builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateInteger(),function);
                return ofac.getTypedTerm(function, COL_TYPE.INTEGER);
            } else { // Its COUNT(*)

                Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, ofac.getVariable("*"));
                return ofac.getTypedTerm(function, COL_TYPE.INTEGER);
            }
        } else if (expr instanceof Avg) {

            return ofac.getFunction(OBDAVocabulary.SPARQL_AVG, getBooleanTerm( expr.getArg()));
            //builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);

        } else if (expr instanceof Sum) {
            return  ofac.getFunction(OBDAVocabulary.SPARQL_SUM, getBooleanTerm( expr.getArg()));
            //builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);

        } else if (expr instanceof Min) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_MIN, getBooleanTerm( expr.getArg()));
            //builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);

        } else if (expr instanceof Max) {
            return ofac.getFunction(OBDAVocabulary.SPARQL_MAX, getBooleanTerm( expr.getArg()));
            //builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);

        }
        throw new RuntimeException("The expression " + expr + " is not supported yet!");
    }
    private Term getBinaryExpression(BinaryValueOperator expr) {
        ValueExpr arg1 = expr.getLeftArg(); // get the first argument
        Term term1 = getExpression(arg1);
        ValueExpr arg2 = expr.getRightArg(); // get the second argument
        Term term2 = getExpression(arg2);
        if (expr instanceof And) {
            return ofac.getFunctionAND(term1, term2);
        }
        else if (expr instanceof Or) {
            return ofac.getFunctionOR(term1, term2);
        }
        else if (expr instanceof SameTerm) {
            return ofac.getFunctionEQ(term1, term2);
        }
        else if (expr instanceof Regex) { // sesame regex is Binary, Jena N-ary
            Regex reg = (Regex) expr;
            ValueExpr flags = reg.getFlagsArg();
            Term term3 = (flags != null) ? getExpression(flags) : OBDAVocabulary.NULL;
            return ofac.getFunction(OBDAVocabulary.SPARQL_REGEX, term1, term2, term3);
        }
        else if (expr instanceof Compare) {
            switch (((Compare) expr).getOperator()) {
                case EQ:
                    return ofac.getFunctionEQ(term1, term2);
                case GE:
                    return ofac.getFunctionGTE(term1, term2);
                case GT:
                    return ofac.getFunctionGT(term1, term2);
                case LE:
                    return ofac.getFunctionLTE(term1, term2);
                case LT:
                    return ofac.getFunctionLT(term1, term2);
                case NE:
                    return ofac.getFunctionNEQ(term1, term2);
            }
        }
        else if (expr instanceof MathExpr) {
            switch (((MathExpr)expr).getOperator()) {
                case PLUS:
                    return ofac.getFunctionAdd(term1, term2);
                case MINUS:
                    return ofac.getFunctionSubstract(term1, term2);
                case MULTIPLY:
                    return ofac.getFunctionMultiply(term1, term2);
                case DIVIDE:
// TODO: NOT SUPPORTED?
                    break;
            }
        }
        else if (expr instanceof LangMatches) {
            return ofac.getLANGMATCHESFunction(term1, toLowerCase(term2));
        }
        throw new RuntimeException("The expression " + expr + " is not supported yet!");
    }

    private static class TermComparator implements Comparator<Term> {

		@Override
		public int compare(Term arg0, Term arg1) {
			return arg0.toString().compareTo(arg1.toString());
		}

	}

    private List<Term> getUnion(Set<Variable> s1, Set<Variable> s2) {
        // take the union of the *sets* of variables
        Set<Term> vars = new HashSet<>();
        vars.addAll(s1);
        vars.addAll(s2);
        // order is chosen arbitrarily but this is not a problem
        // because it is chosen once and for all
        List<Term> varList = new ArrayList<>(vars);
        return varList;
    }

	public Set<Variable> getVariables(List<org.openrdf.query.algebra.Var> list) {
		Set<Variable> vars = new HashSet<Variable>();
		for (org.openrdf.query.algebra.Var variable : list) {
			if (!variable.hasValue()) { // if it has value, then its a constant
				String name = variable.getName();
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		}
		return vars;
	}
	public Set<Variable> getBindVariables(TupleExpr te) {
		Set<String> names = te.getAssuredBindingNames();
		Set<Variable> vars = new HashSet<Variable>();
		for (String name : names) {
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		return vars;
	}
	
	public Set<Variable> getBindVariables(List<ExtensionElem> elements) {
		Set<Variable> vars = new HashSet<Variable>();
		for (ExtensionElem el : elements) {
				String name = el.getName();
				Variable var = ofac.getVariable(name);
				vars.add(var);
			}
		return vars;
	}

    private Set<Variable> getVariables(Function atom) {
        Set<Variable> set = new HashSet<>();
        for (Term t : atom.getTerms())
            if (t instanceof Variable)
                set.add((Variable)t);
        return set;
    }

	public Set<Variable> getVariables(TupleExpr te) {
		Set<Variable> result = new LinkedHashSet<Variable>();
		if (te instanceof StatementPattern) {
			result.addAll(getVariables(((StatementPattern) te).getVarList()));
		} else if (te instanceof BinaryTupleOperator) {
			result.addAll(getVariables(((BinaryTupleOperator) te).getLeftArg()));
			result.addAll(getVariables(((BinaryTupleOperator) te).getRightArg()));
		} else if (te instanceof UnaryTupleOperator) {
				if (te instanceof Extension) {
					result.addAll(getBindVariables(((Extension) te).getElements()));
				}
			result.addAll(getVariables(((UnaryTupleOperator) te).getArg()));
		} else {
			throw new RuntimeException("Operator not supported: " + te);
		}
		return result;
	}

    private List<Term> getUnionOfVariables(Function a1, Function a2) {
    // take the union of the *sets* of variables
        Set<Term> vars = new HashSet<>();
        for (Term t : a1.getTerms())
            if (t instanceof Variable)
                vars.add(t);
        for (Term t : a2.getTerms())
            if (t instanceof Variable)
                vars.add(t);
    // order is chosen arbitrarily but this is not a problem
    // because it is chosen once and for all
        List<Term> varList = new ArrayList<>(vars);
        return varList;
    }

    private CQIE createRule(DatalogProgram pr, String headName, List<Term> headParameters, Function... body) {
        Predicate pred = ofac.getPredicate(headName, headParameters.size());
        Function head = ofac.getFunction(pred, headParameters);
        CQIE rule = ofac.getCQIE(head, body);
        pr.appendRule(rule);
        return rule;
    }


	//private Variable getFreshVariable(int[] count) {
	//	count[0] += 1;
	//	return ofac.getVariable("VAR" + count[0]);
	//}

	public ValueConstant getConstant(LiteralImpl literal) {
		URI type = literal.getDatatype();
		COL_TYPE objectType = getDataType(literal);
		String value = literal.getLabel();
		ValueConstant constant = ofac.getConstantLiteral(value, objectType);

		/*
		 * Validating that the value is correct (lexically) with respect to the
		 * specified datatype
		 */
		
		if (type != null) {
			boolean valid = XMLDatatypeUtil.isValidValue(value, type);
			if (!valid)
				throw new RuntimeException(
						"Invalid lexical form for datatype. Found: " + value);
		}
		return constant;

	}


	private COL_TYPE getDataType(LiteralImpl node) {

		URI typeURI = node.getDatatype();
		// if null return literal, and avoid exception
		if (typeURI == null) 
			return COL_TYPE.LITERAL;
		
		COL_TYPE dataType = dtfac.getDataType(typeURI);
        if (dataType == null) 
			throw new RuntimeException("Unsupported datatype: " + typeURI.stringValue());
		
        if (dataType == COL_TYPE.DECIMAL) { 
			// special case for decimal
			String value = node.getLabel().toString();
			if (!value.contains(".")) {
				// Put the type as integer (decimal without fractions).
				dataType = COL_TYPE.INTEGER;
			}
		} 
		return dataType;
	}

	private Term getBooleanTerm(ValueExpr expr) {
		if (expr instanceof Var) {
			return getVariableTerm((Var) expr);
		} else if (expr instanceof org.openrdf.query.algebra.ValueConstant) {
			return getConstantFunctionTerm((org.openrdf.query.algebra.ValueConstant) expr);
		} else if (expr instanceof UnaryValueOperator) {
			return getBuiltinFunctionTerm((UnaryValueOperator) expr);
		} else if (expr instanceof BinaryValueOperator) {
			if (expr instanceof Regex) { // sesame regex is Binary, Jena N-ary
				Regex reg = (Regex) expr;
				ValueExpr arg1 = reg.getLeftArg(); 
				ValueExpr arg2 = reg.getRightArg(); 
				ValueExpr flags = reg.getFlagsArg();
				Term term1 = getBooleanTerm(arg1);
				Term term2 = getBooleanTerm(arg2);
				Term term3 = (flags != null) ? getBooleanTerm(flags) : OBDAVocabulary.NULL;
				return ofac.getFunction(
						OBDAVocabulary.SPARQL_REGEX, term1, term2, term3);
			}
			BinaryValueOperator function = (BinaryValueOperator) expr;
			ValueExpr arg1 = function.getLeftArg(); // get the first argument
			ValueExpr arg2 = function.getRightArg(); // get the second argument
			Term term1 = getBooleanTerm(arg1);
			Term term2 = getBooleanTerm(arg2);
			// Construct the boolean function
			// TODO Change the method name because ExprFunction2 is not only for
			// boolean functions
			return getBooleanFunction(function, term1, term2);
		} else if (expr instanceof Bound){
			
			return ofac.getFunctionIsNotNull(getVariableTerm(((Bound) expr).getArg()));
		} else {
			throw new RuntimeException("The builtin function "
					+ expr.toString() + " is not supported yet!");
		}
	}
	
	private Term getVariableTerm(Var expr) {
		return getOntopTerm(expr);
		
	}

	private Function getConstantFunctionTerm(org.openrdf.query.algebra.ValueConstant expr) {
		Function constantFunction = null;
		Value v = expr.getValue();

		if (v instanceof LiteralImpl) {
			LiteralImpl lit = (LiteralImpl)v;
			URI type = lit.getDatatype();
			COL_TYPE tp;
			if (type == null) {
				tp = COL_TYPE.LITERAL;
			}
			else {
				tp = dtfac.getDataType(type);
				if (tp == null) {
					return ofac.getUriTemplateForDatatype(type.stringValue());
				}				
			}
			
			String constantString;
			switch (tp) {
				case INTEGER:
				case NEGATIVE_INTEGER:
				case NON_POSITIVE_INTEGER:
				case POSITIVE_INTEGER:
				case NON_NEGATIVE_INTEGER:
					constantString = lit.integerValue().toString();
					break;
				case LONG:
					constantString = lit.longValue() + "";
					break;
				case DECIMAL:
					constantString = lit.decimalValue().toString();
					break;
				case FLOAT:
					constantString = lit.floatValue() + "";
					break;
				case DOUBLE:
					constantString = lit.doubleValue() + "";
					break;
				case INT:
				case UNSIGNED_INT:
					constantString = lit.intValue() + "";
					break;
				case DATETIME:
				case YEAR:
				case DATE:
				case TIME:
					constantString = lit.calendarValue().toString();
					break;
				case BOOLEAN:
					constantString = lit.booleanValue() + "";
					break;
				case STRING:
				case LITERAL:
					constantString = lit.stringValue() + "";
					break;
				default:
					throw new RuntimeException("Undefiend datatype: " + tp);
			}
			ValueConstant constant = ofac.getConstantLiteral(constantString, tp);
			constantFunction = ofac.getTypedTerm(constant, tp);	
		} 
		else if (v instanceof URIImpl) {
            constantFunction = uriTemplateMatcher.generateURIFunction(v.stringValue());
            if (constantFunction.getArity() == 1)
                constantFunction = ofac.getUriTemplateForDatatype(((URIImpl) v).stringValue());
		}
		
		return constantFunction;
	}

	private Function getBuiltinFunctionTerm(UnaryValueOperator expr) {
		Function builtInFunction = null;
		if (expr instanceof Not) {
			ValueExpr arg = expr.getArg();
			Term term = getBooleanTerm(arg);
			builtInFunction = ofac.getFunctionNOT(term);
		}
		/*
		 * The following expressions only accept variable as the parameter
		 */

		else if (expr instanceof IsLiteral) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_LITERAL, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof IsURI) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_URI, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof Str) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_STR, getBooleanTerm( expr.getArg()));
			
		} else if (expr instanceof Datatype) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_DATATYPE, getBooleanTerm( expr.getArg()));
		
		} else if (expr instanceof IsBNode) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_IS_BLANK, getBooleanTerm( expr.getArg()));
							
		} else if (expr instanceof Lang) {
			ValueExpr arg = expr.getArg();
			if (arg instanceof Var) {
				builtInFunction = ofac.getFunction(
						OBDAVocabulary.SPARQL_LANG,
						getVariableTerm((Var) arg));
			}
		} else if (expr instanceof Count) {
			if (expr.getArg() != null) {
				Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, getBooleanTerm( expr.getArg()));
				//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateInteger(),function);
				builtInFunction = ofac.getTypedTerm(function, COL_TYPE.INTEGER);
			} else { // Its COUNT(*)
				
				Function function = ofac.getFunction(OBDAVocabulary.SPARQL_COUNT, ofac.getVariable("*"));
				builtInFunction = ofac.getTypedTerm(function, COL_TYPE.INTEGER);
			}
		} else if (expr instanceof Avg) {
			
			builtInFunction  = ofac.getFunction(OBDAVocabulary.SPARQL_AVG, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Sum) {
			builtInFunction  =  ofac.getFunction(OBDAVocabulary.SPARQL_SUM, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Min) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_MIN, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} else if (expr instanceof Max) {
			builtInFunction = ofac.getFunction(OBDAVocabulary.SPARQL_MAX, getBooleanTerm( expr.getArg()));
			//builtInFunction = ofac.getFunction(	ofac.getDataTypePredicateDecimal(),function);
			
		} 
		else {
			throw new RuntimeException("The builtin function "
					+ expr.toString() + " is not supported yet!");
		}
		return builtInFunction;
	}

	private Function getBooleanFunction(BinaryValueOperator expr, Term term1,
			Term term2) {
		Function function = null;
		// The AND and OR expression
		if (expr instanceof And) {
			function = ofac.getFunctionAND(term1, term2);
		} else if (expr instanceof Or) {
			function = ofac.getFunctionOR(term1, term2);
		}
		// The other expressions
		else if (expr instanceof SameTerm){
			function = ofac.getFunctionEQ(term1, term2);
		} else if (expr instanceof Compare) {
			CompareOp operator = ((Compare) expr).getOperator();
			if (operator == Compare.CompareOp.EQ)
				function = ofac.getFunctionEQ(term1, term2);
			else if (operator == Compare.CompareOp.GE)
				function = ofac.getFunctionGTE(term1, term2);
			else if (operator == Compare.CompareOp.GT)
				function = ofac.getFunctionGT(term1, term2);
			else if (operator == Compare.CompareOp.LE)
				function = ofac.getFunctionLTE(term1, term2);
			else if (operator == Compare.CompareOp.LT)
				function = ofac.getFunctionLT(term1, term2);
			else if (operator == Compare.CompareOp.NE)
				function = ofac.getFunctionNEQ(term1, term2);
		} else if (expr instanceof MathExpr) {
			MathOp mop = ((MathExpr)expr).getOperator();
			if (mop == MathOp.PLUS) 
				function = ofac.getFunctionAdd(term1, term2);
			else if (mop == MathOp.MINUS)
				function = ofac.getFunctionSubstract(term1, term2);
			else if (mop == MathOp.MULTIPLY) 
				function = ofac.getFunctionMultiply(term1, term2);
		} else if (expr instanceof LangMatches) {
			function = ofac.getLANGMATCHESFunction(term1, toLowerCase(term2));
		} else {
			throw new IllegalStateException("getBooleanFunction does not understand the expression " + expr);
		}
		return function;
	}

	private Term toLowerCase(Term term) {
		Term output = term;
		if (term instanceof Function) {
			Function f = (Function) term;
			Predicate functor = f.getFunctionSymbol();
			if (functor instanceof DataTypePredicate) {
				Term functionTerm = f.getTerm(0);
				if (functionTerm instanceof Constant) {
					Constant c = (Constant) functionTerm;
					output = ofac.getFunction(functor, 
							 ofac.getConstantLiteral(c.getValue().toLowerCase(), 
							 c.getType()));
				}
			}
		}
		return output;
	}
	
	public void getSignature(ParsedQuery query, List<String> signatureContainer) {
		signatureContainer.clear();
		if (query instanceof ParsedTupleQuery || query instanceof ParsedGraphQuery) {
			TupleExpr te = query.getTupleExpr();
			signatureContainer.addAll(te.getBindingNames());
		}
	}
	
//	public void getSignature(Query query, List<String> signatureContainer) {
//		signatureContainer.clear();
//		if (query.isSelectType() || query.isDescribeType()) {
//			signatureContainer.addAll(query.getResultVars());
//
//		} else if (query.isConstructType()) {
//			Template constructTemplate = query.getConstructTemplate();
//			for (Triple triple : constructTemplate.getTriples()) {
//				/*
//				 * Check if the subject, predicate, object is a variable.
//				 */
//				Node subject = triple.getSubject(); // subject
//				if (subject instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) subject).getName();
//					signatureContainer.add(vs);
//				}
//				Node predicate = triple.getPredicate(); // predicate
//				if (predicate instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) predicate).getName();
//					signatureContainer.add(vs);
//				}
//				Node object = triple.getObject(); // object
//				if (object instanceof com.hp.hpl.jena.sparql.core.Var) {
//					String vs = ((com.hp.hpl.jena.sparql.core.Var) object).getName();
//					signatureContainer.add(vs);
//				}
//			}
//		}
//	}

}
