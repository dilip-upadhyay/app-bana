import { WorkflowMetadata } from '../models/WorkflowMetadata';

export class WorkflowHistory {
    private undoStack: WorkflowMetadata[] = [];
    private redoStack: WorkflowMetadata[] = [];
    private maxHistory: number;

    constructor(maxHistory = 50) {
        this.maxHistory = maxHistory;
    }

    /**
     * Pushes a new state to the history stack.
     * Call this BEFORE applying changes to the current state.
     */
    push(state: WorkflowMetadata) {
        this.undoStack.push(structuredClone(state));
        if (this.undoStack.length > this.maxHistory) {
            this.undoStack.shift();
        }
        this.redoStack = []; // Clear redo stack on new action
    }

    undo(currentState: WorkflowMetadata): WorkflowMetadata | null {
        if (this.undoStack.length === 0) return null;

        const previousState = this.undoStack.pop()!;
        this.redoStack.push(structuredClone(currentState));

        return previousState;
    }

    redo(currentState: WorkflowMetadata): WorkflowMetadata | null {
        if (this.redoStack.length === 0) return null;

        const nextState = this.redoStack.pop()!;
        this.undoStack.push(structuredClone(currentState));

        return nextState;
    }

    get canUndo(): boolean {
        return this.undoStack.length > 0;
    }

    get canRedo(): boolean {
        return this.redoStack.length > 0;
    }
}
