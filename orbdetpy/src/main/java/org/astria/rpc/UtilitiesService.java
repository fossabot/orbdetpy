/*
 * UtilitiesService.java - Utilities service handler.
 * Copyright (C) 2019-2020 University of Texas
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

package org.astria.rpc;

import java.util.ArrayList;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.astria.Measurements;
import org.astria.Utilities;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.files.ccsds.TDMParser.TDMFileFormat;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Predefined;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.Ephemeris;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

public final class UtilitiesService extends UtilitiesGrpc.UtilitiesImplBase
{
    @Override public void importTDM(Messages.ImportTDMInput req, StreamObserver<Messages.Measurement2DArray> resp)
    {
	try
	{
	    ArrayList<ArrayList<Measurements.Measurement>> mlist = Utilities.importTDM(req.getFileName(), TDMFileFormat.values()[req.getFileFormat()]);
	    Messages.Measurement2DArray.Builder outer = Messages.Measurement2DArray.newBuilder();
	    for (ArrayList<Measurements.Measurement> m: mlist)
	    {
		Messages.MeasurementArray.Builder inner = Messages.MeasurementArray.newBuilder()
		    .addAllArray(Tools.buildResponseFromMeasurements(m));
		outer = outer.addArray(inner);
	    }
	    resp.onNext(outer.build());
	    resp.onCompleted();
	}
	catch (Throwable exc)
	{
	    resp.onError(new StatusRuntimeException(Status.INTERNAL.withDescription(Tools.getStackTrace(exc))));
	}
    }

    @Override public void interpolateEphemeris(Messages.InterpolateEphemerisInput req, StreamObserver<Messages.MeasurementArray> resp)
    {
	try
	{
	    Frame fromFrame = FramesFactory.getFrame(Predefined.valueOf(req.getSourceFrame()));
	    Frame toFrame = FramesFactory.getFrame(Predefined.valueOf(req.getDestFrame()));
	    ArrayList<SpacecraftState> states = new ArrayList<SpacecraftState>(req.getTimeCount());
	    for (int i = 0; i < req.getTimeCount(); i++)
	    {
		List<Double> pv = req.getEphem(i).getArrayList();
		TimeStampedPVCoordinates tspv = new TimeStampedPVCoordinates(AbsoluteDate.J2000_EPOCH.shiftedBy(req.getTime(i)),
									     new Vector3D(pv.get(0), pv.get(1), pv.get(2)),
									     new Vector3D(pv.get(3), pv.get(4), pv.get(5)));
		states.add(new SpacecraftState(new CartesianOrbit(tspv, fromFrame, Constants.EGM96_EARTH_MU)));
	    }

	    AbsoluteDate tm = AbsoluteDate.J2000_EPOCH.shiftedBy(req.getInterpStart());
	    AbsoluteDate interpEnd = AbsoluteDate.J2000_EPOCH.shiftedBy(req.getInterpEnd());
	    ArrayList<Measurements.Measurement> output = new ArrayList<Measurements.Measurement>(2*req.getTimeCount());
	    Ephemeris interpolator = new Ephemeris(states, req.getNumPoints());
	    while (true)
	    {
		output.add(new Measurements.Measurement(interpolator.getPVCoordinates(tm, toFrame), null));
		double deltat = interpEnd.durationFrom(tm);
		if (deltat <= 0.0)
		    break;
		tm = new AbsoluteDate(tm, FastMath.min(deltat, req.getStepSize()));
	    }

	    Messages.MeasurementArray.Builder builder = Messages.MeasurementArray.newBuilder().addAllArray(Tools.buildResponseFromMeasurements(output));
	    resp.onNext(builder.build());
	    resp.onCompleted();
	}
	catch (Throwable exc)
	{
	    resp.onError(new StatusRuntimeException(Status.INTERNAL.withDescription(Tools.getStackTrace(exc))));
	}
    }
}
