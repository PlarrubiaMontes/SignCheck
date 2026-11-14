import { ChangeDetectionStrategy, Component, ElementRef, input, output, signal, viewChild } from '@angular/core';

/**
 * Drop zone for files that also works with the keyboard: it is focusable, Enter or Space opens the file
 * chooser, and it is announced as a button with the given label.
 */
@Component({
  selector: 'app-file-drop',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="drop"
      role="button"
      tabindex="0"
      [class.drop--over]="dragOver()"
      [attr.aria-label]="label()"
      (click)="input.click()"
      (keydown.enter)="$event.preventDefault(); input.click()"
      (keydown.space)="$event.preventDefault(); input.click()"
      (dragover)="onDragOver($event)"
      (dragleave)="dragOver.set(false)"
      (drop)="onDrop($event)"
    >
      <span class="drop__icon" aria-hidden="true">⬆</span>
      <span>{{ label() }}</span>
    </div>
    <input #input type="file" hidden [multiple]="multiple()" (change)="onChange($event)" />
  `,
  styles: `
    .drop {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2rem 1rem;
      border: 2px dashed var(--border-strong);
      border-radius: 12px;
      background: var(--surface);
      cursor: pointer;
      text-align: center;
    }
    .drop:focus-visible { outline: 3px solid var(--focus); outline-offset: 2px; }
    .drop--over { border-color: var(--accent); background: var(--accent-bg); }
    .drop__icon { font-size: 1.75rem; }
  `,
})
export class FileDrop {
  readonly label = input.required<string>();
  readonly multiple = input(false);
  readonly filesSelected = output<File[]>();

  protected readonly dragOver = signal(false);
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.emit(event.dataTransfer?.files ?? null);
  }

  protected onChange(event: Event): void {
    this.emit((event.target as HTMLInputElement).files);
    this.input().nativeElement.value = ''; // allow choosing the same file again
  }

  private emit(list: FileList | null): void {
    const files = list ? Array.from(list) : [];
    if (files.length > 0) {
      this.filesSelected.emit(this.multiple() ? files : files.slice(0, 1));
    }
  }
}
