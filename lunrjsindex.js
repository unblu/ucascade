var documents = [

{
    "id": 0,
    "uri": "tech-docs/20_endpoints.html",
    "menu": "tech-docs",
    "title": "Endpoints",
    "text": " Table of Contents Endpoints Main endpoint Blocking endpoint Replay endpoint Readiness and liveness probes Endpoints Main endpoint POST &lt;server url&gt;/ucascade/merge-request This is the principal endpoint, that receive the Merge Request Webhook event sent by GitLab. Requests are processed asynchronously, meaning that GitLab will receive a 202 Accepted response back immediately. Example: { build_commit : 6af21ad, build_timestamp : 2022-01-01T07:21:58.378413Z, gitlab_event_uuid : 62940263-b495-4f7e-b0e8-578c7307f13d } build_commit and build_timestamp allow you to identify the ucascade version. gitlab_event_uuid is the value received in the X-Gitlab-Event-UUID header. Blocking endpoint A secondary endpoint where the process in done in a blocking way is available as well: POST &lt;server url&gt;/ucascade/merge-request-blocking With this blocking endpoint the different actions made by ucascade (see Technical documentation ) are visible in the returned response. Example: { build_commit : 6af21ad, build_timestamp : 2022-01-01T07:21:58.378413Z, gitlab_event_uuid : 62940263-b495-4f7e-b0e8-578c7307f13d, previous_auto_mr_merged : { id : 34, project_id : 1, mr_number : 34, title : [ucascade] Auto MR: 'main/1.2.x' -&gt; 'main/1.3.x' (!29), description : Automatic cascade merge request: `test` !29 --&gt; `main/1.2.x` --&gt; `main/1.3.x`, state : opened, merge_status : can_be_merged, has_conflicts : false, source_branch : mr29_main/1.2.x, target_branch : main/1.3.x, web_url : https://gitlab.example.com/root/some-project/-/merge_requests/34, ucascade_state : MERGED }, created_auto_mr : { id : 38, project_id : 1, mr_number : 38, assignee_id : 40, title : [ucascade] Auto MR: 'main/1.3.x' -&gt; 'develop' (!25), description : Automatic cascade merge request: `test` !24 --&gt; `main/1.2.x` !25 --&gt; `main/1.3.x` --&gt; develop, state : opened, merge_status : cannot_be_merged, has_conflicts : true, source_branch : mr25_main/1.3.x, target_branch : main, web_url : https://gitlab.example.com/root/some-project/-/merge_requests/38, ucascade_state : NOT_MERGED_CONFLICTS }, existing_branch_deleted : { branch_name : mr21_main/1.2.x } } Since GitLab keeps the response obtained when delivering a webhook event and displays it in the webhook admin page, using this endpoint might be interesting for debugging purpose. Replay endpoint An additional endpoint is available to trigger the process using some simplified input compared to the merge-request event body sent by the GitLab Webhook API. POST &lt;server url&gt;/ucascade/replay Body: { project_id : 23, mr_number : 122, user_id : 9382, source_branch : feature/1.3.x/some-change, target_branch : main/1.3.x, mr_state : merged, merge_commit_sha : 0c6f9d312b924bff313f60db2f269b5f4901cd95, mr_action : merge, gitlab_event_uuid : replay-394720 } The response is the same as in the blocking case. Using this endpoint is interesting to trigger again the ucascade action for a given event using curl , without having to send the complete webhook event body. Readiness and liveness probes The application provides standard probes: &lt;server url&gt;/q/health/live : The application is up and running (liveness). &lt;server url&gt;/q/health/ready : The application is ready to serve requests (readiness). &lt;server url&gt;/q/health : Accumulating all health check procedures in the application. "
},

{
    "id": 1,
    "uri": "tech-docs/10_setup.html",
    "menu": "tech-docs",
    "title": "Setup",
    "text": " Table of Contents Setup GitLab: Generate an API Token Application Setup GitLab: Webhook Setup Branches: Merge Model Dev setup Working with a remote GitLab instance Setup In order to interact with a given Gitlab instance through its REST API, ucascade needs to be authorized and authenticated. To do so, together with Gitlab&#8217;s instance URL, an API token must be provided. In case merge-requests require one approval to be merged, an additional API token must be provided. If the merge-request author is allowed to approve its own merge-requests, both API tokens can be identical. GitLab: Generate an API Token You will need a personal access token or a group access in order for the tool to interact with your repositories on GitLab. In addition, depending on the project&#8217;s configuration, you might need an extra API token, which can be obtained in the same way as the first one. All the actions are done using the REST API of GitLab. You will need the api scope. Application Setup Some configurations are available for ucascade: Example application.properties file gitlab.host=https://gitlab.com gitlab.api.token=glpat-rXzx1n17cqUnmo437XSf gitlab.api.token.approver=glpat-fGzx1n17cqUnmo437GGs You can use any of the Configuration Sources supported by Quarkus to set the values. For example you can use following system property to set the gitlab.api.token value: Setting gitlab.api.token using a system property: export GITLAB_API_TOKEN=glpat-rXzx1n17cqUnmo437XSf GitLab host Specify the location of the GitLab server: key: gitlab.host default value https://gitlab.com GitLab api token Specify the api token value used when ucascade is performing REST calls. key: gitlab.api.token No default value. Mandatory for the application to start GitLab api token approver Specify the api token value used when ucascade is approving a merge-request through a REST call. key: gitlab.api.token.approver No default value. If not set, ucascade will merge the merge-requests without approving them first. If set, ucascade will approve the merge-request using that value. GitLab: Webhook Setup In the corresponding repository or group configure a Webhook pointing to the location where ucascade is available: URL: &lt;server url&gt;/ucascade/merge-request Trigger: Merge request events Warning From an operational point of view, it might be safer to deploy ucascade to a server where only your GitLab instance has access. Branches: Merge Model As explained in the configuration file section , in order to configure how the automatic merges are performed, a JSON file named ucascade.json must be present at the root of the project. Dev setup The application can be started locally, check local build section. Working with a remote GitLab instance If you are working locally with a remote gitlab instance (like https://gitlab.com/ ), adding some proxy might be useful: With a tool like ngrok you will get a public url (something like https://2a01-8943-19d-e0a-8b20-645f-f7a2-c2d-9be1.ngrok.io ) that points to your localhost computer. start ngrok (assuming ucascade is running locally on port 8080) ngrok http 8080 With a tool like mitmproxy you can proxy the remote instance to capture the REST requests made by ucascade to the remote instance: start mitmproxy mitmproxy -p 8888 --mode reverse:https://gitlab.com And then make ucascade use localhost:8888 instead of gitlab.com directly: use mitmproxy export GITLAB_HOST=http://localhost:8888 "
},

{
    "id": 2,
    "uri": "tech-docs/11_ucascade-configuration-file.html",
    "menu": "tech-docs",
    "title": "Configuration File",
    "text": " Table of Contents Configuration file Configuration file The branches of interest, and the direction and order of the consecutive automatic merges, is configured in the JSON file ucascade.json . This file must be present in the root directory of the repository. Example: { branches : [ { sourceBranchPattern : main/1\\.2\\.x, targetBranch : main/1.3.x }, { sourceBranchPattern : main/1\\.3\\.x/[0-9]{4}\\.[0-9]{2}, targetBranch : main/1.3.x }, { sourceBranchPattern : main/1\\.3\\.x, targetBranch : main/2.0.x }, { sourceBranchPattern : main/2\\.0\\.x/[0-9]{4}\\.[0-9]{2}, targetBranch : main/2.0.x } ] } "
},

{
    "id": 3,
    "uri": "tech-docs/40_unit-test-example-files.html",
    "menu": "tech-docs",
    "title": "Unit test files",
    "text": " Table of Contents Unit test example files Unit test example files When the unit tests are running, the GitLab server is mocked. Wiremock is serving example responses from the src/test/resources/gitlab_template_json folder. Those files can be generated by the code in the ResetTestExampleFilesMain class. Adjust the location of the GitLab server ( URL constant). It can be a local GitLab instance or a remote one like gitlab.com. Adjust the access token ( TOKEN constant) The Webhook event is received on a server running on localhost:9999 (started by ResetTestExampleFilesMain ). If this server is not accessible by GitLab, start a tool like ngrok (with ngrok http 9999 ) and adjust the hook server value ( HOOK_URL constant) If you decide to use http://localhost:9999 for HOOK_URL (without ngrok), then you might need to change a setting in your local GitLab instance (see Allow webhook and service requests to local network ). If your gitlab instance is running in a docker container, be sure to run ResetTestExampleFilesMain in docker as well or make the host network available for the containers. "
},

{
    "id": 4,
    "uri": "tech-docs/30_technical-documentation.html",
    "menu": "tech-docs",
    "title": "Workflow",
    "text": " Table of Contents High-level Workflow Technical Workflow - ucascade Merge Request events Actions High-level Workflow In this example, one automatic cascade merge is to be performed. E.g., User is merging feature/1.3.x/my_feature into main/1.3.x , which has as target the final branch develop . When the merge event targets a final branch, ucascade ignores it. Similarly, when a merge request created automatically by ucascade cannot be merged due to conflicts between the source and target branches, ucascade cannot continue and the execution is aborted. If properly configured, Gitlab notifies the users about the conflicting MR. Technical Workflow - ucascade When using the blocking or replay endpoints , the returned object contains some indications about the items created by the different actions. If one of those actions is failing, the error message will be displayed in the returned object with the _error suffix. Merge Request events Merge event For a given merge request merge event, 3 different independent actions can be made: previous_auto_mr_merged : if the merged-request event corresponds to the merge of an auto merge-request, and some other auto-merge requests are open between the same two branches, then the oldest auto merge request is merged. created_auto_mr : if the merged-request event corresponds to the merge of any merge request that requires an auto merge-request to be created (to cascade the change to the next branch), it is created and potentially directly merged. With the blocking and replay endpoints, the returned object contains a created_auto_mr.ucascade_state field indicating if the action was successful or why it was not. It can take the values of: MERGED: MR was created and merged succesfully NOT_MERGED_CONFLICTS: MR was created but not merged due to existing conflicts between source and target branches that must be manually resolved NOT_MERGED_CONCURRENT_MRS: MR was created but not merged due to another existing open merge request between the same source and target branches NOT_MERGED_UNKNOWN_REASON: MR was created but not merged due to an unknown/unexpected reason existing_branch_deleted : if the merged-request event corresponds to the merge of an auto merge-request and the source branch is still present, it gets deleted. The failure of one of those action will not prevent the next to be executed. Close event For a given merge request close event, 2 different independent actions can be made: existing_branch_deleted : if the merge-request event corresponds to the close of an auto merge-request, the source branch gets deleted previous_auto_mr_merged : if some other auto-merge requests are open between the same two branches, then the oldest auto merge request is merged. Actions previous_auto_mr_merged When a lot of changes are made concurrently, it can be that multiple auto-merge requests are created between the same main branches ( main/1.2.x and main/1.3.x ). In such cases, only the first one is set up to be merged directly. This means that when the auto-merge request finally gets merged or closed, any other pending auto-merge requests between the same branches must still be merged. ucascade starts with the oldest one. created_auto_mr This explains how ucascade determines if a new auto-merge request is created or not: During the final stage of the success case&#8201;&#8212;&#8201;merging the MR&#8201;&#8212;&#8201; ucascade verifies if that MR has any pipeline configured. If it has, the flag merge_when_pipeline_succeeds is set to true , meaning that the merge is only to happen after all its pipelines have finished successfully. Otherwise, if it doesn&#8217;t have any pipeline, this flag is set to false , preventing GitLab to return a 405 error (see issue #355455 for more details). existing_branch_deleted Whenever a user closes an auto merge-request, the source branch is not deleted automatically. Additionally, sometimes when a merge-request is merged, its source branch is not deleted by gitlab. Ucascade makes sure that in both of these situations, the source branch gets deleted. "
},

{
    "id": 5,
    "uri": "tech-docs/50_build.html",
    "menu": "tech-docs",
    "title": "Build",
    "text": " Table of Contents Build Running the application locally Packaging the application Build a docker image Run the docker image Build Please refer to the Quarkus documentation for more details. Running the application locally You can run your application in dev mode that enables live coding using: ./gradlew --console=PLAIN quarkusDev This will start the application is dev mode, available on port 8080 . For more details check the Quarkus Gradle Tooling page. Packaging the application The application can be packaged using: ./gradlew build It produces the quarkus-run.jar file in the build/quarkus-app/ directory. Be aware that it’s not an über-jar as the dependencies are copied into the build/quarkus-app/lib/ directory. The application is now runnable using java -jar build/quarkus-app/quarkus-run.jar . If you want to build an über-jar , execute the following command: ./gradlew build -Dquarkus.package.type=uber-jar The application, packaged as an über-jar , is now runnable using java -jar build/ucascade-&lt;version&gt;-runner.jar . Build a docker image ./gradlew build \ -Dquarkus.container-image.build=true \ -Dquarkus.container-image.push=true \ -Dquarkus.container-image.registry=&lt;registry name&gt; \ -Dquarkus.container-image.group=&lt;image path&gt; \ -Dquarkus.container-image.name=&lt;image name&gt; \ -Dquarkus.container-image.username=&lt;registry username&gt; \ -Dquarkus.container-image.password=&lt;registry password&gt; Run the docker image docker run -p 8080:8080 -e GITLAB_API_TOKEN=glpat-rXzx1n17cqUnmo437XSf &lt;ucascade image name&gt; The server is running on the 8080 port. "
},

{
    "id": 6,
    "uri": "user-docs/user-guide.html",
    "menu": "user-docs",
    "title": "User Guide",
    "text": " Table of Contents User guide Happy case: you needn&#8217;t do anything Other case: the cascade MR can&#8217;t be merged User guide Suppose you have a setup where ucascade is already configured correctly (see Setup ), and you have a merge request ( !17 ) where the branch some-change targets an old branch of main ( main/1.2.x ): You expect this change to go through the cascade: some-change &#8594; main/1.2.x &#8594; main/1.3.x &#8594; main/2.0.x (see MultiMain Git branching model ). This situation can have one of two outcomes: either the cascade goes through with no intervention required on your part, or it doesn&#8217;t. Happy case: you needn&#8217;t do anything Once you have merged, the tool creates a follow-up MR (called cascade MR in this description) whose title starts with [ucascade] Auto MR . This cascade MR is configured to be merged automatically as soon as the pipeline succeeds, or immediately if no pipeline is configured. And because 2 merges are needed for the complete cascade, the same pattern will happen again to obtain a merge between the main branches main/1.3.x and main/2.0.x . Because two merges are required for the complete cascade, the same pattern applies again to obtain a merge between the main branches main/1.3.x and main/2.0.x . At the end of the process, the original change is applied on all the relevant main branches. The MultiMain Git flow is implemented correctly. Multiple MRs betwen the same main branches When multiple developers are working on the same project simultaneously, it can happen that each of them merges an MR targeting the same main branch, for example main/1.3.x . In this case, the first cascade MR between 2 main branches ( main/1.3.x and main/2.0.x in the example) is merged. The second cascade MR between the same main branches stays open until the first one is merged. After the merge of the first cascade MR the tool proceeds with the merge of the next Auto MR. Other case: the cascade MR can&#8217;t be merged When the tool cannot merge an MR directly, it sets the assignee to the user who merged the original MR. The reason for the merge failing can be anything, such as: A merge conflict An approval issue The pipeline failing (because of a flaky test, for example) You have different options: continue to use the cascade MR or bypass it. 1— Continue to use the cascade MR As with any regular MR, you must perform the action necessary to be able to merge the MR (resolve conflict locally or online, add the missing approval, re-run a pipeline…). Then you can merge it, just like any other MR. Important Don&#8217;t check the [ ] squash checkbox. You need to preserve the information about the commit that the cascade MR was created from. 2— Bypass the cascade MR If you directly merge the main branch ( main/1.2.x for example) that the source branch was created from ( mr17_main/1.2.x in the example) into the target branch (the other main branch, main/1.3.x in the example), GitLab automatically marks the cascade MR as merged when the merge commit is pushed and ucascade deletes the unused source branch ( mr17_main/1.2.x ). "
},

{
    "id": 7,
    "uri": "tech-docs/index.html",
    "menu": "tech-docs",
    "title": "Introduction",
    "text": " Table of Contents Technical documentation Technical documentation ucascade is a Quarkus web service capable of orchestrating the cascade merging between branches of a Gitlab repository. This section provide technical details about the project. "
},

{
    "id": 8,
    "uri": "user-docs/multi-main.html",
    "menu": "user-docs",
    "title": "MultiMain",
    "text": " Table of Contents MultiMain Git branching model One main branch per maintained version stream Making a change to multiple versions Important points Tooling support Appendix MultiMain Git branching model The MultiMain Git branching model is useful for teams maintaining multiple versions of a project concurrently over a long time period (months or years). It is similar to the git-flow branching model. One main branch per maintained version stream In the MultiMain branching model, you have one main branch per maintained version stream. Imagine you support patch releases for: version stream 1.2.x released versions in this stream: 1.2.0 , 1.2.1 , 1.2.2 &#8230;&#8203; 1.2.13 , 1.2.14 , &#8230;&#8203; version stream 1.3.x released versions in this stream: 1.3.0 &#8230;&#8203; 1.3.6 , 1.3.7 , 1.3.8 &#8230;&#8203; Version stream 1.2.x Released versions in this stream: 1.2.0 , 1.2.1 , 1.2.2 ,&#8230;&#8203;, 1.2.13 , 1.2.14 ,&#8230;&#8203; Version stream 1.3.x Released versions in this stream: 1.3.0 ,&#8230;&#8203;, 1.3.6 , 1.3.7 , 1.3.8 &#8230;&#8203; At the same time, you&#8217;re preparing the next major version 2.0.0 , with its own dedicated stream 2.0.x . This results in three main branches ( main/1.2.x , main/1.3.x , and main/2.0.x ), each corresponding to a different version stream. The naming pattern main/&lt;stream&gt; , where &lt;stream&gt; is the name of the stream, is fairly common in such cases. The &lt;stream&gt; part of branch names is a matter of project organization. If you work with a major.minor.patch version pattern, but maintain only one version per major version (version 2 , version 3 , version 4 and so on), the streams may be called 2.x.x , 3.x.x , or 4.x.x . In that case, the branches might be named: main/2.x.x main/3.x.x main/4.x.x You could also drop the x.x suffix and only use: main/2 main/3 main/4 Which naming convention you end up using is up to your team. Making a change to multiple versions Some Git flows, such as the GitLab flow , recommend developing on the main branch and cherry-picking relevant changes to the branches corresponding to older maintained versions. In the MultiMain flow, you alway do the change first the oldest version you are supporting ( main/1.2.x in the example) and you apply the same change through all the branches corresponding to the branches you are supporting. The key advantage of this flow is that it&#8217;s designed toensure the change is applied everywhere. It eliminates the risk that someone forgets to cherry-pick a commit. Additionally, you only have to solve potential merge conflicts once, when applying the change the first time, and not every time you cherry-pick a commit to an older branch. You can define that the developer who makes the change must apply their change to all branches. When you work with cherry-picking, it&#8217;s often up to the build engineer to backport changes. Important points Always merge forward It can happen that a change is only relevant for older branches and not for the newer versions (because the feature fixed on an older branch no longer exists in the newer version). Sometimes, a change is only relevant for older branches, for example because the feature fixed on an older branch no longer exists in newer versions. In such cases, you must still merge through the different branches with an empty merge commit. In the diagram below, the feature corresponding to the commits f1 and f2 is no longer present in version 2.0.x and should only be merged into the branch main/1.3.x : To make sure the branch feature/1.3.x/some-change is only merged into main/1.3.x , after having created the first merge commit m , check out the branch main/2.0.x and run the following command: git merge -s ours main/1.3.x This way the developer doing the next change on an older version will not have any conflict or unfinished merge problems when he tries to cascade his change through the branches. Bottleneck If there is a conflict between 2 main branches when cascading a given change, this can create a bottleneck (the subsequent changes that should also be cascaded are stuck). It might take time to solve the conflicts to create the merge commit that correspond to the cascade of the change. The solution to this problem is to work with two feature branches: A second branch feature/2.0.x/some-change , branched from main/2.0.x is created. The branch feature/1.3.x/some-change is merged into feature/2.0.x/some-change (this creates commit f3 ) Once the 2 features branches are ready (it doesn&#8217;t matter how much time it takes to have everything looking good). Two merge requests can be opened for each feature branch: First feature/2.0.x/some-change is merged to main/2.0.x Then feature/1.3.x/some-change is merge to main/1.3.x which can be directly cascaded to main/2.0.x since the conflict was already resolved with the first merge request. Tooling support Bitbucket See Bitbucket&#8217;s automatic branch merging feature. GitLab This project implements the MultiMain branching model for GitLab. The ucascade tool creates the necessary follow-up merge requests when an initial merge request is merged into a main branch. Appendix Legacy naming Some projects more in line with the git-flow terminology give their main branches different names: release/1.2.x release/1.3.x develop (for the version 2.0.x ) Only the names of the branches differ, but the idea is the same. Having a different name for develop doesn´t provide any benefits, since it&#8217;s no different from the other “release” branches. Also, occasional contributors to a project may find it difficult to determine which version the develop branch corresponds to. "
},

{
    "id": 9,
    "uri": "user-docs/index.html",
    "menu": "user-docs",
    "title": "Introduction",
    "text": " Table of Contents User documentation User documentation This section describes the MultiMain Git workflow and how to use the tool . "
},

{
    "id": 10,
    "uri": "search.html",
    "menu": "-",
    "title": "search",
    "text": " Search Results "
},

{
    "id": 11,
    "uri": "lunrjsindex.html",
    "menu": "-",
    "title": "null",
    "text": " will be replaced by the index "
},

];
