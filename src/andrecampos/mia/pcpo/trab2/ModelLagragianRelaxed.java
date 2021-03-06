package andrecampos.mia.pcpo.trab2;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;
import ilog.cplex.IloCplex.UnknownObjectException;
import andrecampos.mia.pcpo.data.Arc;
import andrecampos.mia.pcpo.data.Demand;
import andrecampos.mia.pcpo.data.Graph;
import andrecampos.mia.pcpo.data.Node;

/**
 * Modelo para o Trabalho 2
 * @author Andre Campos
 */
public class ModelLagragianRelaxed {

	private final Graph 	graph;
	private final IloCplex 	cplex;

	private final Demand[] 	demands;
	private final Arc[] 		arcs;
	private final Node[] 		nodes;
	private IloIntVar[] y;
	private IloNumVar[][] x;

	public ModelLagragianRelaxed(Graph graph) throws IloException {
		super();
		this.graph = graph;
		cplex 		= new IloCplex();
        cplex.setOut(null);
		demands 	= graph.demands;
		arcs 		= graph.arcs;
		nodes		= graph.nodes;
		createModel();
	}

	/**
	 *
	 * @param mi
	 * @return
	 * @throws IloException
	 */
	public double calculateObjectiveFunction(double[] mi) throws IloException {
		//// funcao objetivo
		IloLinearNumExpr cost = cplex.linearNumExpr();
        // custo variavel
        objectiveVariableCosts(cost);
        // custo fixo
        objectiveFixedCosts(cost);
		// funcao relaxada
		relaxedCapacityFunction(cost,mi);
        cplex.addMinimize(cost);
        cplex.solve();
        return cplex.getObjValue();
	}

    public void clearObjective() throws IloException {
        cplex.remove(cplex.getObjective()); // ensures the minimize function will be removed
    }


	private void relaxedCapacityFunction(IloLinearNumExpr cost, double[] mi) throws IloException {
		for(Arc arc : arcs) {
			for (int k = 0; k < demands.length; k++) {
                cost.addTerm(mi[arc.id], x[k][arc.id]);
			}
            cost.addTerm(-mi[arc.id] * arc.capacity, y[arc.id]);
		}
	}


	private void objectiveVariableCosts(IloLinearNumExpr cost)
			throws IloException {
		for (int k = 0; k < demands.length; k++) {
			for (int i = 0; i < arcs.length; i++) {
				cost.addTerm(arcs[i].variableCost, x[k][i]);
			}
		}
	}


	private void objectiveFixedCosts(IloLinearNumExpr cost) throws IloException {
		for (int i = 0; i < arcs.length; i++) {
			cost.addTerm(y[i], arcs[i].fixedCost);
		}
	}


	private void createModel() throws IloException {
		defineYVariables();

		defineXVariables();

		restrictDepamandsFromSourceToTargetNodes();
	}

	private void defineXVariables() throws IloException {
		// Definicao da quantidade de fluxo da demanda k no arco i,j
		x = new IloNumVar[graph.getSizeDemands()][graph.getSizeArcs()];
		for (int k = 0; k < demands.length; k++) {
			for(Arc arc : arcs) {
				setX(k, arc);
			}
		}
	}

	private void defineYVariables() throws IloException {
		// Definicao das variaveis binarias yij que indicam se o arco e' utilizado ou nao
		y = new IloIntVar[arcs.length];
		for(Arc arc : arcs) {
			y[arc.id] = cplex.boolVar("y"+arc.name());
		}
	}

	private void restrictDepamandsFromSourceToTargetNodes() throws IloException {
		Demand demand;
		Node node;
		IloLinearNumExpr flow;
		for (int i = 0; i < nodes.length; i++) {
			node = nodes[i];

			for (int k = 0; k < demands.length; k++) {
				flow = cplex.linearNumExpr();
				// soma custos saindo do vertice
				for (Arc arc : node.out) {
					flow.addTerm(1, x[k][arc.id]);
				}
				// subtrai custos entrando no vertice
				for (Arc arc : node.in) {
					flow.addTerm(-1, x[k][arc.id]);
				}

				demand = demands[k];
				if(node.id == demand.sDemand) {
					cplex.addEq(flow, demand.demand);
				} else if(node.id == demand.tDemand){
					cplex.addEq(flow, -demand.demand);
				} else {
					cplex.addEq(flow, 0);
				}
			}
		}
	}

	private void setX(int k, Arc arc) throws IloException {
		x[k][arc.id] = cplex.numVar(0, Double.MAX_VALUE, "x"+(k+1)+arc.name());
	}

	public Status getStatus() throws IloException {
		return cplex.getStatus();
	}

	public void exportLP(String dir) throws IloException {
		cplex.exportModel(dir);
	}

	public double[] getY() throws UnknownObjectException, IloException {
		return cplex.getValues(y);
	}

	public double[][] getX() throws UnknownObjectException, IloException {
		double [][] x = new double[demands.length][];
		for (int k = 0; k < x.length; k++) {
			x[k] = cplex.getValues(this.x[k]);
		}
		return x;
	}
}
