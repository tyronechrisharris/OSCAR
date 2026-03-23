/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.consys.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import org.sensorhub.api.command.ICommandStatus;
import org.sensorhub.api.command.ICommandStreamInfo;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.common.IdEncoders;
import org.sensorhub.api.database.IObsSystemDatabase;
import org.sensorhub.impl.service.consys.SWECommonUtils;
import org.sensorhub.impl.service.consys.ServiceErrors;
import org.sensorhub.impl.service.consys.resource.PropertyFilter;
import org.sensorhub.impl.service.consys.resource.RequestContext;
import org.sensorhub.impl.service.consys.resource.ResourceBinding;
import org.sensorhub.impl.service.consys.resource.ResourceFormat;
import org.sensorhub.impl.service.consys.resource.ResourceLink;
import org.sensorhub.impl.service.consys.task.CommandStatusHandler.CommandStatusHandlerContextData;
import org.vast.cdm.common.DataStreamParser;
import org.vast.cdm.common.DataStreamWriter;


public class CommandResultBindingSweCommon extends ResourceBinding<BigId, ICommandStatus>
{
    CommandStatusHandlerContextData contextData;
    DataStreamParser resultReader;
    DataStreamWriter resultWriter;

    
    public CommandResultBindingSweCommon(RequestContext ctx, IdEncoders idEncoders, boolean forReading, IObsSystemDatabase db) throws IOException
    {
        super(ctx, idEncoders);
        this.contextData = (CommandStatusHandlerContextData)ctx.getData();
        
        var csInfo = contextData.csInfo;
        if (forReading)
        {
            var is = ctx.getInputStream();
            resultReader = getSweCommonParser(csInfo, is, ctx.getFormat());
        }
        else
        {
            var os = ctx.getOutputStream();
            resultWriter = getSweCommonWriter(csInfo, os, ctx.getPropertyFilter(), ctx.getFormat());
            
            // if request is coming from a browser, use well-known mime type
            // so browser can display the response
            if (ctx.isBrowserHtmlRequest())
            {
                if (ctx.getFormat().equals(ResourceFormat.SWE_TEXT))
                    ctx.setResponseContentType(ResourceFormat.TEXT_PLAIN.getMimeType());
                else if (ctx.getFormat().equals(ResourceFormat.SWE_XML))
                    ctx.setResponseContentType(ResourceFormat.APPLI_XML.getMimeType());
                else
                    ctx.setResponseContentType(ctx.getFormat().getMimeType());
            }
            else
                ctx.setResponseContentType(ctx.getFormat().getMimeType());
        }
    }
    
    
    @Override
    public ICommandStatus deserialize() throws IOException
    {
        throw ServiceErrors.notWritable();
    }


    @Override
    public void serialize(BigId key, ICommandStatus status, boolean showLinks) throws IOException
    {
        // if embedded result
        var obsList = status.getResult().getObservations();
        if (obsList != null)
        {
            for (var obs: obsList)
                resultWriter.write(obs.getResult());
        }
    }
    
    
    protected DataStreamParser getSweCommonParser(ICommandStreamInfo csInfo, InputStream is, ResourceFormat format) throws IOException
    {
        var dataParser = SWECommonUtils.getParser(csInfo.getResultStructure(), csInfo.getResultEncoding(), format);
        dataParser.setInput(is);
        return dataParser;
    }
    
    
    protected DataStreamWriter getSweCommonWriter(ICommandStreamInfo csInfo, OutputStream os, PropertyFilter propFilter, ResourceFormat format) throws IOException
    {
        var dataWriter = SWECommonUtils.getWriter(csInfo.getResultStructure(), csInfo.getResultEncoding(), format);
        dataWriter.setOutput(os);
        return dataWriter;
    }


    @Override
    public void startCollection() throws IOException
    {
        resultWriter.startStream(true);
    }


    @Override
    public void endCollection(Collection<ResourceLink> links) throws IOException
    {
        resultWriter.endStream();
        resultWriter.flush();
    }
}
