/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.command;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.data.IObsData;
import org.sensorhub.api.data.ObsData;
import org.sensorhub.utils.ObjectUtils;
import org.vast.util.Asserts;
import net.opengis.swe.v20.DataBlock;


/**
 * <p>
 * Immutable class used to describe the result of a command
 * </p>
 *
 * @author Alex Robin
 * @date Sep 10, 2022
 */
public class CommandResult implements ICommandResult
{
    protected BigId dataStreamID;
    protected Collection<BigId> obsIDs;
    protected Collection<IObsData> obsList;
    
    
    protected CommandResult()
    {
        // can only instantiate with builder or static methods
    }
    
    
    /**
     * Declare an entire datastream as result
     * @param dataStreamID The ID of the datastream that contains the result
     * @return The result object
     */
    public static ICommandResult withEntireDatastream(BigId dataStreamID)
    {
        var res = new CommandResult();
        res.dataStreamID = Asserts.checkNotNull(dataStreamID, "dataStreamID");
        return res;
    }
    
    
    /**
     * Declare certain obs from an existing datastream as result
     * @param dataStreamID The ID of the datastream that contains the result
     * @param obsIDs IDs of observations that constitute the result
     * @return The result object
     */
    public static ICommandResult withObsInDatastream(BigId dataStreamID, Collection<BigId> obsIDs)
    {
        var res = new CommandResult();
        res.dataStreamID = Asserts.checkNotNull(dataStreamID, "dataStreamID");
        res.obsIDs = Collections.unmodifiableCollection(Asserts.checkNotNullOrEmpty(obsIDs, "obsIDs"));
        return res;
    }
    
    
    /**
     * Add observations to a command result
     * @param obsList List of observations to be added to the datastream of the result
     * @return The result object
     */
    public static ICommandResult withObs(Collection<IObsData> obsList)
    {
        var res = new CommandResult();
        res.obsList = Collections.unmodifiableCollection(Asserts.checkNotNull(obsList, "obsList"));
        return res;
    }
    
    
    /**
     * Add observations to a command result
     * @param obs A single observation to return as the result
     * @return The result object
     */
    public static ICommandResult withSingleObs(IObsData obs)
    {
        var res = new CommandResult();
        res.obsList = Set.of(Asserts.checkNotNull(obs, IObsData.class));
        return res;
    }
    
    
    /**
     * Add data to a command result
     * @param data A single observation result to return as the command result
     * @return The result object
     */
    public static ICommandResult withData(DataBlock data)
    {
        var res = new CommandResult();
        var obs = new ObsData.Builder()
            .withPhenomenonTime(Instant.now())
            .withResult(data)
            .build();
        res.obsList = Set.of(Asserts.checkNotNull(obs, IObsData.class));
        return res;
    }


    @Override
    public Collection<IObsData> getObservations()
    {
        return obsList;
    }


    @Override
    public Collection<BigId> getObservationRefs()
    {
        return obsIDs;
    }


    @Override
    public BigId getDataStreamID()
    {
        return dataStreamID;
    }
    
    
    @Override
    public String toString()
    {
        return ObjectUtils.toString(this, true);
    }
}
