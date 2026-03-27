/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2022 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.command;

import java.util.Collection;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.data.IObsData;


/**
 * <p>
 * Interface for command results
 * </p><p>
 * Several ways it can work:
 * <ol>
 * <li>Process creates a dedicated datastream, ingests obs in it, then provides
 * the datastream ID in the command result</li> 
 * <li>Process uses a persistent output datastream, ingests obs in it, then
 * provides only obs IDs (can publish to several different datastreams) in
 * the command result</li> 
 * <li>Process provides a list of datablock inline. In this case,
 * the result is not stored in a separate datastream and is only accessible via
 * the command channel. This is typically used for on-demand processes where only
 * the user who called the process (i.e. sent the command) is interested by the
 * result)</li>
 * </p>
 *
 * @author Alex Robin
 * @since Sep 10, 2022
 */
public interface ICommandResult
{
    /**
     * @return inline result data, as a list of observations matching the
     * result schema defined by {@link ICommandStreamInfo}
     */
    Collection<IObsData> getObservations();
    
    /**
     * @return reference to observations (when not provided inline) generated
     * during the execution of the command and added to one or more existing
     * datastream(s).
     */
    Collection<BigId> getObservationRefs();
    
    /**
     * @return reference to an entire datastream that contains one or more
     * observations (but usually many) generated during the execution of the
     * command.
     */
    BigId getDataStreamID();
}
