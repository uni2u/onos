/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 ONOS GUI -- Port View Module
 */

(function () {
    'use strict';

    // injected references
    var $log, $scope, $location, fs, ts, tbs;

    angular.module('ovPort', [])
    .controller('OvPortCtrl',
        ['$log', '$scope', '$location',
            'FnService', 'TableService', 'TableBuilderService',

        function (_$log_, _$scope_, _$location_, _fs_, _ts_, _tbs_) {
            var params;
            $log = _$log_;
            $scope = _$scope_;
            $location = _$location_;
            fs = _fs_;
            ts = _ts_;
            tbs = _tbs_;

            params = $location.search();
            if (params.hasOwnProperty('devId')) {
                $scope.devId = params['devId'];
            }

            tbs.buildTable({
                scope: $scope,
                tag: 'port',
                query: params
            });

            $scope.refresh = function () {
                $log.debug('Refreshing ports page');
                ts.resetSortIcons();
                $scope.sortCallback();
            };
            
            $log.log('OvPortCtrl has been created');
        }]);
}());