/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.imap.message.response;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponse.Type;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnpooledStatusResponseFactory extends AbstractStatusResponseFactory implements StatusResponseFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnpooledStatusResponseFactory.class);

    @Override
    protected StatusResponse createResponse(Type type, Tag tag, ImapCommand command, HumanReadableText displayTextKey, ResponseCode code) {
        return new ImmutableStatusResponse(type, tag, command, displayTextKey, code);
    }

}
