import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

@Component({
  selector: 'ab-slide-toggle',
  standalone: true,
  imports: [CommonModule, MatSlideToggleModule],
  templateUrl: './slide-toggle.html',
  styleUrl: './slide-toggle.css'
})
export class SlideToggle {
  @Input() label: string = '';
  @Input() disabled: boolean = false;
  @Input() checked: boolean = false;
  @Output() checkedChange = new EventEmitter<boolean>();
}
