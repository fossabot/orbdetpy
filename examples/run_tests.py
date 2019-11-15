# run_tests.py - Program to run simulation and OD tests.
# Copyright (C) 2019 University of Texas
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

import os
import sys
import json
import math
import time
from threading import Semaphore
from orbdetpy.estimation import determine_orbit
from orbdetpy.simulation import simulate_measurements

def callback(data, extra):
    sem.release()

print("run_tests start : %s" % time.strftime("%Y-%m-%d %H:%M:%S"))
odpdir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
datdir = os.path.join(odpdir, "examples", "data")

lock_count = 0
sem = Semaphore(0)
for root, dirs, files in os.walk(datdir):
    outdir = os.path.join(root, "output")
    if ("output" in dirs):
        dirs.remove("output")
    else:
        os.makedirs(outdir)

    for fname in files:
        idx = fname.find("sim_cfg.json")
        if (idx == -1):
            continue
        lock_count += 1
        print("Simulating {}".format(fname))
        simulate_measurements(os.path.join(root, fname), async_callback = callback,
                              output_file = os.path.join(outdir, fname[:idx] + "obs.json"))

for i in range(lock_count):
    sem.acquire()

lock_count = 0
sem = Semaphore(0)
for root, dirs, files in os.walk(datdir):
    outdir = os.path.join(root, "output")
    if ("output" in dirs):
        dirs.remove("output")

    for fname in files:
        idx = fname.find("od_cfg.json")
        if (idx == -1):
            continue

        print("Fitting {}".format(fname))
        with open(os.path.join(outdir, fname[:idx] + "obs.json"), "r") as fp:
            obs = json.load(fp)

        for algo in ["EKF", "UKF"]:
            with open(os.path.join(root, fname), "r") as fp:
                config = json.load(fp)
                config["Estimation"]["Filter"] = algo
            lock_count += 1
            determine_orbit(config, obs, async_callback = callback,
                            output_file = os.path.join(outdir, fname[:idx] + algo + "_fit.json"))

for i in range(lock_count):
    sem.acquire()

for root, dirs, files in os.walk(datdir):
    outdir = os.path.join(root, "output")
    if ("output" in dirs):
        dirs.remove("output")

    for fname in files:
        idx = fname.find("od_cfg.json")
        if (idx == -1):
            continue

        with open(os.path.join(outdir, fname[:idx] + "obs.json"), "r") as fp:
            obs = json.load(fp)
        exact = [x for x in obs if ("Station" in x or "PositionVelocity" in x)]

        for algo in ["EKF", "UKF"]:
            output = []
            with open(os.path.join(outdir, fname[:idx] + algo + "_fit.json"), "r") as fp:
                fit = json.load(fp)
            estim = [x for x in fit if ("PreFit" in x and "PostFit" in x)]

            for exa, est in zip(exact, estim):
                if ("TrueStateCartesian" in exa):
                    X = exa["TrueStateCartesian"][:6]
                else:
                    X = exa["PositionVelocity"][:6]
                diff = [X[i] - est["EstimatedState"][i] for i in range(6)]
                sigm = [3.0*math.sqrt(est["EstimatedCovariance"][i][i]) for i in range(6)]
                output.append({"Time" : exa["Time"], "Station" : exa.get("Station"),
                               "StateResidual" : diff, "Covariance3Sigma" : sigm,
                               "WithinBounds" : [diff[i] > -sigm[i] and diff[i] < sigm[i] for i in range(6)]})

            with open(os.path.join(outdir, fname[:idx] + algo + "_diff.json"), "w") as fp:
                json.dump(output, fp, indent = 1)

print("run_tests end   : %s" % time.strftime("%Y-%m-%d %H:%M:%S"))
