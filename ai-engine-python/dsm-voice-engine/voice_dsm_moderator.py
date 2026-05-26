import asyncio
import json
import websockets

class DSMVoiceModerator:
    def __init__(self):
        self.active_calls = {}
        self.ai_is_speaking = False

    async def session_handler(self, websocket, path):
        """
        Manages real-time voice session streaming with Teams bot or custom WebRTC gateway.
        """
        print("[Voice DSM] Activated voice stream session.")
        try:
            async for message in websocket:
                event = json.loads(message)
                
                # Event 1: Audio Packet Ingested
                if event.get("type") == "audio-inbound":
                    audio_payload = event.get("payload")
                    transcript = await self.transcribe_audio_stream(audio_payload)
                    await self.process_spoken_update(transcript, websocket)
                
                # Event 2: User Barge-in / Interruption
                elif event.get("type") == "barge-in-detected":
                    print("[Voice DSM] User interruption detected. Halting AI speech output.")
                    self.ai_is_speaking = False
                    await self.halt_audio_playback(websocket)

        except websockets.exceptions.ConnectionClosed:
            print("[Voice DSM] Voice session connection closed.")

    async def transcribe_audio_stream(self, audio_bytes) -> str:
        # Integrates with Deepgram or Azure Speech Services via WebSockets
        # Simulated return value based on speech input
        return "I completed work on task ADO-102 and today I will deploy the gateway, no blockers."

    async def process_spoken_update(self, transcript: str, ws):
        print(f"[Voice DSM] Transcribed speech: '{transcript}'")
        
        # Core NLP parsing to trigger ADO updates
        if "completed" in transcript.lower():
            # Update ADO status to "Done" automatically
            update_payload = {
                "type": "board-update-action",
                "taskId": "ADO-102",
                "status": "Done"
            }
            await ws.send(json.dumps(update_payload))
            
            # Speak confirmation back
            response_text = "Excellent. I have updated task ADO-102 to completed. What is next on your plate?"
            await self.synthesize_and_speak(response_text, ws)

    async def synthesize_and_speak(self, text: str, ws):
        # Uses ElevenLabs or Azure Speech to generate audio
        if not self.ai_is_speaking:
            self.ai_is_speaking = True
            audio_out = {
                "type": "audio-outbound",
                "text": text,
                "payload": "BASE64_PCM_AUDIO_DATA"
            }
            await ws.send(json.dumps(audio_out))

    async def halt_audio_playback(self, ws):
        halt_payload = {"type": "stop-playback"}
        await ws.send(json.dumps(halt_payload))

# Instantiate server loop
if __name__ == "__main__":
    moderator = DSMVoiceModerator()
    start_server = websockets.serve(moderator.session_handler, "0.0.0.0", 8001)
    print("Voice DSM Server running on port 8001")
    asyncio.get_event_loop().run_until_complete(start_server)
    asyncio.get_event_loop().run_forever()
