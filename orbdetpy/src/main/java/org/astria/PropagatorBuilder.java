/*
 * PropagatorBuilder.java - Wrapper for Orekit's propagator builder.
 * Copyright (C) 2018-2021 University of Texas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.astria;

import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.conversion.ODEIntegratorBuilder;
import org.orekit.propagation.integration.AdditionalEquations;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.utils.ParameterDriversList;

public final class PropagatorBuilder extends NumericalPropagatorBuilder
{
    private final Settings odCfg;
    private final DMCEquations dmcEqns;
    protected boolean enableDMC;

    public PropagatorBuilder(Settings cfg, Orbit orb, ODEIntegratorBuilder ode, PositionAngle ang, double pos, boolean enableDMC)
    {
	super(orb, ode, ang, pos);
	this.odCfg = cfg;
	this.dmcEqns = new DMCEquations();
	this.enableDMC = enableDMC;
    }

    @Override public NumericalPropagator buildPropagator(double[] par)
    {
	NumericalPropagator prop = super.buildPropagator(par);
	if (odCfg.estmDMCCorrTime > 0.0 && odCfg.estmDMCSigmaPert > 0.0)
	{
	    prop.addAdditionalEquations(dmcEqns);
	    ParameterDriversList plst = getPropagationParametersDrivers();
	    prop.setInitialState(prop.getInitialState().addAdditionalState(Estimation.DMC_ACC_PROP, plst.findByName(Estimation.DMC_ACC_ESTM[0]).getValue(),
									   plst.findByName(Estimation.DMC_ACC_ESTM[1]).getValue(),
									   plst.findByName(Estimation.DMC_ACC_ESTM[2]).getValue()));
	}
	return(prop);
    }

    class DMCEquations implements AdditionalEquations
    {
	@Override public String getName()
	{
	    return(Estimation.DMC_ACC_PROP);
	}

	@Override public double[] computeDerivatives(SpacecraftState sta, double[] pdot)
	{
	    double[] accEci = new double[6];
	    if (enableDMC)
	    {
		double[] acc = sta.getAdditionalState(Estimation.DMC_ACC_PROP);
		for (int i = 0; i < 3; i++)
		{
		    accEci[i+3] = acc[i];
		    pdot[i] = -acc[i]/odCfg.estmDMCCorrTime;
		}
	    }
	    else
	    {
		for (int i = 0; i < 3; i++)
		    pdot[i] = 0.0;
	    }
	    return(accEci);
	}
    }
}
