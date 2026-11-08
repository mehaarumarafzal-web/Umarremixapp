// ===== GLOBAL VARIABLES =====
let audioContext = null;
let audioBuffer = null;
let sourceNode = null;
let gainNode = null;
let isPlaying = false;
let startTime = 0;
let pauseTime = 0;
let activeEffects = {};
let timelineBlocks = [];
let customEffects = [];
let undoStack = [];
let redoStack = [];
let loopEnabled = false;
let loopStart = 0;
let loopEnd = 0;
let animationId = null;

// ===== EFFECT DEFINITIONS =====
const effects = {
    bass: [
        { name: 'Bass Boost', color: '#f093fb' },
        { name: 'Deep 808', color: '#f5576c' },
        { name: 'Sub Drop', color: '#ee5a6f' }
    ],
    remix: [
        { name: 'Echo', color: '#4facfe' },
        { name: 'Reverb', color: '#00f2fe' },
        { name: '8D Audio', color: '#43e97b' },
        { name: 'Slowed', color: '#38f9d7' },
        { name: 'Speed Up', color: '#fa709a' },
        { name: 'Underwater', color: '#fee140' },
        { name: 'Stutter', color: '#a18cd1' },
        { name: 'Reverse', color: '#fbc2eb' }
    ],
    sound: [
        { name: 'Air Horn', color: '#ff6b6b' },
        { name: 'Siren', color: '#ffd93d' },
        { name: 'Vinyl Scratch', color: '#6bcb77' },
        { name: 'Riser', color: '#4d96ff' },
        { name: 'Impact', color: '#ff6b9d' }
    ]
};

// ===== INIT =====
function init() {
    createEffectButtons();
    loadCustomEffects();
    renderTimeline();
}

function initAudioContext() {
    if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (audioContext.state === 'suspended') {
        audioContext.resume();
    }
}

// ===== LOAD SONG =====
function loadSong(event) {
    const file = event.target.files[0];
    if (!file) return;

    initAudioContext();
    document.getElementById('songInfo').textContent = 'Loading: ' + file.name;

    const reader = new FileReader();
    reader.onload = function(e) {
        audioContext.decodeAudioData(e.target.result, function(buffer) {
            audioBuffer = buffer;
            pauseTime = 0;
            document.getElementById('songInfo').textContent = '🎵 ' + file.name;
            drawWaveform();
            updateTimeDisplay();
            showToast('Song loaded successfully!');
        }, function(err) {
            showToast('Error loading audio!');
        });
    };
    reader.readAsArrayBuffer(file);
}

// ===== WAVEFORM =====
function drawWaveform() {
    const canvas = document.getElementById('waveformCanvas');
    const ctx = canvas.getContext('2d');
    canvas.width = canvas.offsetWidth * 2;
    canvas.height = 160;

    const data = audioBuffer.getChannelData(0);
    const step = Math.ceil(data.length / canvas.width);
    const amp = canvas.height / 2;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#4facfe';

    for (let i = 0; i < canvas.width; i++) {
        let min = 1.0, max = -1.0;
        for (let j = 0; j < step; j++) {
            const datum = data[(i * step) + j];
            if (datum < min) min = datum;
            if (datum > max) max = datum;
        }
        ctx.fillRect(i, (1 + min) * amp, 1, Math.max(1, (max - min) * amp));
    }

    canvas.onclick = function(e) {
        if (!audioBuffer) return;
        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const ratio = x / rect.width;
        pauseTime = ratio * audioBuffer.duration;
        if (isPlaying) {
            sourceNode.stop();
            playFrom(pauseTime);
        }
        updatePlayhead();
        updateTimeDisplay();
    };
}

function updatePlayhead() {
    const playhead = document.getElementById('playhead');
    const canvas = document.getElementById('waveformCanvas');
    if (!audioBuffer) return;

    const currentTime = isPlaying ? (audioContext.currentTime - startTime) : pauseTime;
    const ratio = currentTime / audioBuffer.duration;
    playhead.style.display = 'block';
    playhead.style.left = (10 + ratio * (canvas.offsetWidth - 20)) + 'px';
}

// ===== TIME DISPLAY =====
function updateTimeDisplay() {
    if (!audioBuffer) return;
    const currentTime = isPlaying ? (audioContext.currentTime - startTime) : pauseTime;
    const duration = audioBuffer.duration;
    document.getElementById('timeDisplay').textContent =
        formatTime(currentTime) + ' / ' + formatTime(duration);
}

function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return mins + ':' + secs.toString().padStart(2, '0');
}

// ===== PLAY / PAUSE / STOP =====
function togglePlay() {
    if (!audioBuffer) {
        showToast('Please add a song first!');
        return;
    }
    if (isPlaying) {
        pauseAudio();
    } else {
        playFrom(pauseTime);
    }
}

function playFrom(time) {
    initAudioContext();
    if (sourceNode) {
        try { sourceNode.stop(); } catch(e) {}
    }

    sourceNode = audioContext.createBufferSource();
    sourceNode.buffer = audioBuffer;

    // Apply timeline effects
    const currentTime = time;
    const activeBlockEffects = timelineBlocks
        .filter(b => currentTime >= b.start && currentTime <= b.end)
        .map(b => b.effect);

    gainNode = audioContext.createGain();
    gainNode.gain.value = 1.0;
    sourceNode.connect(gainNode);

    // Apply effect processing
    let lastNode = gainNode;
    activeBlockEffects.forEach(effectName => {
        const effectNode = createEffectNode(effectName);
        if (effectNode) {
            lastNode.connect(effectNode);
            lastNode = effectNode;
        }
    });

    lastNode.connect(audioContext.destination);

    sourceNode.start(0, time);
    startTime = audioContext.currentTime - time;
    isPlaying = true;
    document.getElementById('playBtn').textContent = '⏸ Pause';
    document.getElementById('playBtn').classList.add('playing');

    sourceNode.onended = function() {
        if (isPlaying) {
            if (loopEnabled) {
                playFrom(loopStart);
            } else {
                isPlaying = false;
                pauseTime = 0;
                document.getElementById('playBtn').textContent = '▶ Play';
                document.getElementById('playBtn').classList.remove('playing');
                document.getElementById('playhead').style.display = 'none';
            }
        }
    };

    updateLoop();
}

function pauseAudio() {
    if (sourceNode) {
        try { sourceNode.stop(); } catch(e) {}
        pauseTime = audioContext.currentTime - startTime;
        isPlaying = false;
        document.getElementById('playBtn').textContent = '▶ Play';
        document.getElementById('playBtn').classList.remove('playing');
    }
}

function stopAudio() {
    if (sourceNode) {
        try { sourceNode.stop(); } catch(e) {}
    }
    isPlaying = false;
    pauseTime = 0;
    document.getElementById('playBtn').textContent = '▶ Play';
    document.getElementById('playBtn').classList.remove('playing');
    document.getElementById('playhead').style.display = 'none';
    updateTimeDisplay();
}

// ===== EFFECT NODES =====
function createEffectNode(effectName) {
    if (!audioContext) return null;

    switch(effectName) {
        case 'Bass Boost': {
            const filter = audioContext.createBiquadFilter();
            filter.type = 'lowshelf';
            filter.frequency.value = 200;
            filter.gain.value = 20;
            return filter;
        }
        case 'Deep 808': {
            const filter = audioContext.createBiquadFilter();
            filter.type = 'lowshelf';
            filter.frequency.value = 100;
            filter.gain.value = 30;
            return filter;
        }
        case 'Sub Drop': {
            const filter = audioContext.createBiquadFilter();
            filter.type = 'lowpass';
            filter.frequency.value = 80;
            filter.Q.value = 10;
            return filter;
        }
        case 'Echo': {
            const delay = audioContext.createDelay();
            delay.delayTime.value = 0.3;
            const feedback = audioContext.createGain();
            feedback.gain.value = 0.4;
            const wet = audioContext.createGain();
            wet.gain.value = 0.5;
            delay.connect(feedback);
            feedback.connect(delay);
            delay.connect(wet);
            return delay;
        }
        case 'Reverb': {
            const convolver = audioContext.createConvolver();
            const length = audioContext.sampleRate * 2;
            const impulse = audioContext.createBuffer(2, length, audioContext.sampleRate);
            for (let ch = 0; ch < 2; ch++) {
                const data = impulse.getChannelData(ch);
                for (let i = 0; i < length; i++) {
                    data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, 2);
                }
            }
            convolver.buffer = impulse;
            return convolver;
        }
        case '8D Audio': {
            const panner = audioContext.createStereoPanner();
            const lfo = audioContext.createOscillator();
            const lfoGain = audioContext.createGain();
            lfo.frequency.value = 0.25;
            lfoGain.gain.value = 1;
            lfo.connect(lfoGain);
            lfoGain.connect(panner.pan);
            lfo.start();
            return panner;
        }
        case 'Slowed': {
            sourceNode.playbackRate.value = 0.8;
            return null;
        }
        case 'Speed Up': {
            sourceNode.playbackRate.value = 1.3;
            return null;
        }
        case 'Underwater': {
            const filter = audioContext.createBiquadFilter();
            filter.type = 'lowpass';
            filter.frequency.value = 500;
            filter.Q.value = 5;
            return filter;
        }
        case 'Stutter': {
            const delay = audioContext.createDelay();
            delay.delayTime.value = 0.05;
            const feedback = audioContext.createGain();
            feedback.gain.value = 0.7;
            delay.connect(feedback);
            feedback.connect(delay);
            return delay;
        }
        case 'Reverse': {
            // Reverse is handled at buffer level
            return null;
        }
        default:
            return null;
    }
}

// ===== CREATE EFFECT BUTTONS =====
function createEffectButtons() {
    const bassGrid = document.getElementById('bassEffects');
    const remixGrid = document.getElementById('remixEffects');
    const soundGrid = document.getElementById('soundEffects');

    effects.bass.forEach(effect => {
        bassGrid.appendChild(createEffectButton(effect, 'bass-btn'));
    });
    effects.remix.forEach(effect => {
        remixGrid.appendChild(createEffectButton(effect, 'remix-btn'));
    });
    effects.sound.forEach(effect => {
        soundGrid.appendChild(createEffectButton(effect, 'sound-btn'));
    });
}

function createEffectButton(effect, className) {
    const btn = document.createElement('button');
    btn.className = 'effect-btn ' + className;
    btn.dataset.effect = effect.name;
    btn.innerHTML =
        '<div class="effect-name">' + effect.name + '</div>' +
        '<input type="range" min="0" max="100" value="70" class="intensity-slider" onclick="event.stopPropagation()">';

    btn.addEventListener('mousedown', () => activateEffect(effect.name, btn));
    btn.addEventListener('mouseup', () => deactivateEffect(effect.name, btn));
    btn.addEventListener('mouseleave', () => deactivateEffect(effect.name, btn));
    btn.addEventListener('touchstart', (e) => {
        e.preventDefault();
        activateEffect(effect.name, btn);
    });
    btn.addEventListener('touchend', () => deactivateEffect(effect.name, btn));

    return btn;
}

function activateEffect(effectName, btn) {
    activeEffects[effectName] = true;
    if (btn) btn.classList.add('active');
    showToast('🎵 ' + effectName + ' ON');

    if (isPlaying) {
        pauseTime = audioContext.currentTime - startTime;
        sourceNode.stop();
        playFrom(pauseTime);
    }
}

function deactivateEffect(effectName, btn) {
    activeEffects[effectName] = false;
    if (btn) btn.classList.remove('active');
    showToast('⏹ ' + effectName + ' OFF');

    if (isPlaying) {
        pauseTime = audioContext.currentTime - startTime;
        sourceNode.stop();
        playFrom(pauseTime);
    }
}

// ===== TIMELINE =====
function renderTimeline() {
    const timeline = document.getElementById('timeline');
    timeline.innerHTML = '';

    if (!audioBuffer) return;

    const duration = audioBuffer.duration;
    const timelineWidth = timeline.offsetWidth;

    timelineBlocks.forEach((block, index) => {
        const blockEl = document.createElement('div');
        blockEl.className = 'timeline-block';
        blockEl.style.backgroundColor = block.color;
        blockEl.style.left = (block.start / duration * 100) + '%';
        blockEl.style.width = ((block.end - block.start) / duration * 100) + '%';
        blockEl.innerHTML =
            '<span>' + block.effect + '</span>' +
            '<div class="resize-handle resize-left"></div>' +
            '<div class="resize-handle resize-right"></div>' +
            '<button class="delete-x" onclick="deleteBlock(' + block.id + ')">×</button>';

        // Drag to move
        let isDragging = false;
        let startX = 0;
        let origStart = 0;

        blockEl.addEventListener('mousedown', (e) => {
            if (e.target.classList.contains('resize-handle') || e.target.classList.contains('delete-x')) return;
            isDragging = true;
            startX = e.clientX;
            origStart = block.start;
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!isDragging) return;
            const dx = e.clientX - startX;
            const dt = dx / timelineWidth * duration;
            const newStart = Math.max(0, origStart + dt);
            const dur = block.end - block.start;
            block.start = newStart;
            block.end = newStart + dur;
            renderTimeline();
        });

        document.addEventListener('mouseup', () => {
            if (isDragging) {
                isDragging = false;
                saveToUndo();
            }
        });

        // Resize handles
        const leftHandle = blockEl.querySelector('.resize-left');
        const rightHandle = blockEl.querySelector('.resize-right');

        leftHandle.addEventListener('mousedown', (e) => {
            e.stopPropagation();
            let resizeStart = e.clientX;
            let origS = block.start;

            const onMove = (ev) => {
                const dx = ev.clientX - resizeStart;
                const dt = dx / timelineWidth * duration;
                block.start = Math.max(0, Math.min(origS + dt, block.end - 0.5));
                renderTimeline();
            };
            const onUp = () => {
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
                saveToUndo();
            };
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onUp);
        });

        rightHandle.addEventListener('mousedown', (e) => {
            e.stopPropagation();
            let resizeStart = e.clientX;
            let origE = block.end;

            const onMove = (ev) => {
                const dx = ev.clientX - resizeStart;
                const dt = dx / timelineWidth * duration;
                block.end = Math.min(duration, Math.max(origE + dt, block.start + 0.5));
                renderTimeline();
            };
            const onUp = () => {
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
                saveToUndo();
            };
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onUp);
        });

        timeline.appendChild(blockEl);
    });
}

function deleteBlock(id) {
    timelineBlocks = timelineBlocks.filter(b => b.id !== id);
    saveToUndo();
    renderTimeline();
    showToast('Block deleted');
}

function openBlockModal() {
    if (!audioBuffer) {
        showToast('Please add a song first!');
        return;
    }
    document.getElementById('blockStart').value = pauseTime.toFixed(1);
    document.getElementById('blockEnd').value = (pauseTime + 5).toFixed(1);
    document.getElementById('blockModal').classList.add('show');
}

function saveBlock() {
    const effectName = document.getElementById('blockEffectName').value;
    const start = parseFloat(document.getElementById('blockStart').value);
    const end = parseFloat(document.getElementById('blockEnd').value);
    const color = document.getElementById('blockColor').value;

    if (!effectName) {
        showToast('Please enter effect name!');
        return;
    }
    if (start >= end) {
        showToast('Start must be less than end!');
        return;
    }

    timelineBlocks.push({
        id: Date.now(),
        effect: effectName,
        start: start,
        end: end,
        color: color
    });

    saveToUndo();
    renderTimeline();
    closeModal('blockModal');
    showToast('Block added: ' + effectName);
}

function updateLoop() {
    if (!loopEnabled || !audioBuffer) return;
    const currentTime = audioContext.currentTime - startTime;
    if (currentTime >= loopEnd) {
        sourceNode.stop();
        playFrom(loopStart);
    }
    if (isPlaying) {
        requestAnimationFrame(updateLoop);
    }
}

function toggleLoop() {
    if (!audioBuffer) {
        showToast('Please add a song first!');
        return;
    }
    loopEnabled = !loopEnabled;
    const btn = document.getElementById('loopBtn');
    if (loopEnabled) {
        loopStart = 0;
        loopEnd = audioBuffer.duration;
        btn.textContent = '🔁 Loop: ON';
        btn.classList.add('active');
        showToast('Loop enabled!');
    } else {
        btn.textContent = '🔁 Loop: OFF';
        btn.classList.remove('active');
        showToast('Loop disabled!');
    }
}

function previewAll() {
    if (!audioBuffer) {
        showToast('Please add a song first!');
        return;
    }
    stopAudio();
    pauseTime = 0;
    playFrom(0);
    showToast('▶ Previewing all effects...');
}

// ===== CUSTOM EFFECTS =====
function openCustomModal() {
    document.getElementById('customModal').classList.add('show');
}

function saveCustomEffect() {
    const fileInput = document.getElementById('customAudioInput');
    const name = document.getElementById('customName').value;
    const color = document.getElementById('customColor').value;
    const type = document.getElementById('customType').value;

    if (!name) {
        showToast('Please enter a name!');
        return;
    }

    const effect = {
        id: Date.now(),
        name: name,
        color: color,
        type: type,
        audioData: null
    };

    if (fileInput.files[0]) {
        const reader = new FileReader();
        reader.onload = function(e) {
            effect.audioData = Array.from(new Uint8Array(e.target.result));
            customEffects.push(effect);
            saveCustomEffects();
            renderCustomEffects();
            closeModal('customModal');
            showToast('Custom effect added: ' + name);
        };
        reader.readAsArrayBuffer(fileInput.files[0]);
    } else {
        customEffects.push(effect);
        saveCustomEffects();
        renderCustomEffects();
        closeModal('customModal');
        showToast('Custom effect added: ' + name);
    }
}

function renderCustomEffects() {
    const grid = document.getElementById('customEffects');
    grid.innerHTML = '';

    customEffects.forEach(effect => {
        const btn = document.createElement('button');
        btn.className = 'effect-btn custom-btn';
        btn.dataset.effect = 