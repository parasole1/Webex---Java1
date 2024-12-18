/*
 * Copyright 2020 AppDynamics LLC and its affiliates
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
 *
 */
package com.appdynamics.extensions.process.common;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class CommandExecutor {
    private static Logger logger = ExtensionsLoggerFactory.getLogger(CommandExecutor.class);

    public static List<String> execute(String command) {
        return init(command, null);
    }

    public static List<String> init(String command, String[] env) {
        Process process;
        long startTime = System.currentTimeMillis();
        try {
            logger.debug("Executing the command " + command);
            if (env != null) {
                process = Runtime.getRuntime().exec(command, env);
            } else {
                process = Runtime.getRuntime().exec(command);
            }
            new ErrorReader(process.getErrorStream()).start();
            ResponseParser responseParser = new ResponseParser(process, command);
            responseParser.start();
            process.waitFor();
            responseParser.join();
            long endTime = System.currentTimeMillis() - startTime;
            logger.debug("Executing the command " + command + " ended. Time taken is " + endTime);
            List<String> commandOutput = responseParser.getData();
            if (commandOutput.isEmpty()) {
                logger.error("The process output of the command {} is empty", command);
            }
            return commandOutput;
        } catch (Exception e) {
            logger.error("Error while executing the process " + command, e);
            return null;
        }
    }

    public static List<String> execute(String command, String[] env) {
        return init(command, env);
    }

    public static class ResponseParser extends Thread {

        private Process process;
        private String command;
        private List<String> data = new ArrayList<String>();

        public ResponseParser(Process process, String command) {
            this.process = process;
            this.command = command;
        }

        public void run() {
            InputStream inputStream = process.getInputStream();
            BufferedReader input = null;
            try {
                input = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = input.readLine()) != null) {
                    data.add(line);
                }
            } catch (Exception e) {
                logger.error("Error while reading the input stream from the command " + command, e);
            } finally {
                try {
                    input.close();
                } catch (IOException e) {
                }
                if (process != null) {
                    logger.trace("Destroying the process " + command);
                    process.destroy();
                }
            }
        }

        public List<String> getData() {
            return data;
        }
    }

    public static class ErrorReader extends Thread {
        public static final Logger logger = ExtensionsLoggerFactory.getLogger(ErrorReader.class);

        private final InputStream in;

        public ErrorReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String temp;
            try {
                while (reader.ready() && (temp = reader.readLine()) != null) {
                    logger.error("Process Error - " + temp);
                }
            } catch (IOException e) {
                logger.error("Error while reading the contents of the the error stream", e);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            logger.trace("Closing the Error Reader " + Thread.currentThread().getName());
        }
    }
}
