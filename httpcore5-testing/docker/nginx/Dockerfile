# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM nginx:1.18
MAINTAINER dev@hc.apache.org

ENV var_dir /var/nginx
ENV www_dir ${var_dir}/www

RUN apt-get update
RUN apt-get install -y subversion

RUN mkdir -p ${var_dir}
RUN svn co --depth immediates http://svn.apache.org/repos/asf/httpcomponents/site ${www_dir}
RUN svn up --set-depth infinity ${www_dir}/images
RUN svn up --set-depth infinity ${www_dir}/css

COPY default.conf /etc/nginx/conf.d/default.conf
