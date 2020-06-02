import asyncio
import json
import sys
import threading
import websockets

class K3MepClient:

    def __init__(self):
        self.host = ""
        self.port = 0
        self.endpoint = ""
        self.websocket = None
        self.event_loop = None
        self.event_loop_thread = None
        self.json_responses = []
        self.response_semaphore = None
        self.initialized = False
        self.simulation_running = False
        self.paused = False

    def connect(self, host="localhost", port=8090, endpoint=""):
        self.host = host
        self.port = port
        self.endpoint = endpoint
        self.event_loop = asyncio.new_event_loop()
        self.event_loop_thread = threading.Thread(
                target=lambda: self._start_event_loop())
        self.response_semaphore = threading.Semaphore(0)
        self.event_loop_thread.start()
        asyncio.run_coroutine_threadsafe(self._connect(),
                self.event_loop)
        self.response_semaphore.acquire()

    def _start_event_loop(self):
        self.event_loop.run_forever()

    async def _connect(self):
        uri = f'ws://{self.host}:{self.port}/{self.endpoint}'
        print(f'Connecting to {uri}...')
        self.websocket = await websockets.client.connect(uri)
        print('Connected')
        asyncio.ensure_future(self._response_handler())

    def disconnect(self):
        asyncio.run_coroutine_threadsafe(self._disconnect(),
                self.event_loop)
        self.event_loop.stop()

    async def _disconnect(self):
        await self.websocket.close()

    def initialize(self):
        request = {'type': 'request', 'command': 'initialize'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        self.json_responses.pop()

    def launch(self, model_uri='', model_entry_point='/',
            init_method='', init_args='', method_entry_point=''):
        request = {'type': 'request', 'command': 'launch',
                'arguments': {'modelURI': model_uri,
                'modelEntryPoint': model_entry_point,
                'initializationMethod': init_method,
                'initializationArguments': init_args,
                'methodEntryPoint': method_entry_point}}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            self.simulation_running = True
            self.paused = True

    def continue_(self):
        self.paused = False
        request = {'type': 'request', 'command': 'continue'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)

    def next(self):
        self.paused = False
        request = {'type': 'request', 'command': 'next'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)

    def step_in(self):
        self.paused = False
        request = {'type': 'request', 'command': 'stepIn'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)

    def step_out(self):
        self.paused = False
        request = {'type': 'request', 'command': 'stepIn'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)

    def terminate(self):
        request = {'type': 'request', 'command': 'terminate'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            self.simulation_running = False
            self.paused = False

    def restart(self):
        request = {'type': 'request', 'command': 'restart'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            self.simulation_running = True
            self.paused = True

    def variables(self):
        request = {'type': 'request', 'command': 'variables'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            for variable in response['body']['variables']:
                print(f'{variable["name"]}: {variable["value"]}')

    def stack_trace(self):
        request = {'type': 'request', 'command': 'stackTrace'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            indent = ''
            for stackFrame in response['body']['stackFrames']:
                print(f'{indent}{stackFrame["name"]} (line: {stackFrame["line"]})')
                indent = f'{indent}    '

    def source(self, line_numbers=False):
        request = {'type': 'request', 'command': 'source'}
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)
        else:
            source_content = response["body"]["content"]
            if line_numbers:
                source_content_splitted = source_content.split('\n')
                adjust_index = len(str(len(source_content_splitted)))
                for i in range(len(source_content_splitted)):
                    source_content_splitted[i] = str(i+1).rjust(adjust_index) + ' '\
                            + source_content_splitted[i]
                source_content = '\n'.join(source_content_splitted)
            print(source_content)

    def set_breakpoints(self, lines):
        request = {'type': 'request', 'command': 'setBreakpoints',
                'arguments': {'breakpoints': []}}
        for line in lines:
            request['arguments']['breakpoints'].append({'line': line})
        asyncio.run_coroutine_threadsafe(
                self._send_request(json.dumps(request)),
                self.event_loop)
        self.response_semaphore.acquire()
        response = self.json_responses.pop()
        if response['success'] == False:
            print(f'Error: {response["message"]}', file=sys.stderr)

    async def _send_request(self, request):
        await self.websocket.send(request)

    def _manage_event(self, json_event):
        if json_event['event'] == 'initialized':
            self.initialized = True
        elif json_event['event'] == 'output':
            if json_event['body']['category'] == 'stdout':
                print(json_event['body']['output'], end='')
            else:
                print('Error: Unknown output category: '
                        + json_event['body']['category'],
                        file=sys.stderr)
        elif json_event['event'] == 'stopped':
            if json_event['body']['reason'] == 'step':
                self.paused = True
            elif json_event['body']['reason'] == 'breakpoint':
                self.paused = True
            else:
                print('Error: Unknown stopped reason: '
                        + json_event['body']['reason'],
                        file=sys.stderr)
        elif json_event['event'] == 'terminated':
            self.simulation_running = False
        else:
            print(f'Error: Unsupported event: {json_event["event"]}',
                    file=sys.stderr)

    async def _response_handler(self):
        self.response_semaphore.release()
        async for message in self.websocket:
            json_message = json.loads(message)
            if json_message['type'] == 'response':
                self.json_responses.append(json_message)
                self.response_semaphore.release()
            elif json_message['type'] == 'event':
                self._manage_event(json_message)
